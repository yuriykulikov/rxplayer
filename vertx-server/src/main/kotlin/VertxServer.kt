package de.eso.rxplayer.vertx

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.vertx.core.ServiceHelper
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.spi.VertxFactory

sealed class RequestHandler(
        open val predicate: (String) -> Boolean
) {
    /**
     * @param predicate used to match this handler agains the incoming URI and VERB
     * @param payloadClass class of the expected payload object. Can be null
     * @param handlerFunction a function which will be called if a matching request arrives
     */
    data class GetHandler(
            override val predicate: (String) -> Boolean,
            val handlerFunction: (String, Map<String, String>) -> Observable<out Any>
    ) : RequestHandler(predicate)

    /**
     * @param predicate used to match this handler agains the incoming URI and VERB
     * @param payloadClass class of the expected payload object. Can be null
     * @param handlerFunction a function which will be called if a matching request arrives
     */
    data class PostHandler<P : Any>(
            override val predicate: (String) -> Boolean,
            val payloadClass: Class<P>,
            val handlerFunction: (String, P) -> Single<out Any>
    ) : RequestHandler(predicate)
}

data class WebsocketRequest(val uri: String, val id: String, val subscribe: Boolean)

/**
 * A Vertx-baser web service which serves HTTP and WebSocket on a given port. Actual handling is delegated to given
 * handlers.
 *
 * @param port port to run on
 * @param handlers a list of [RequestHandler] - [de.eso.rxplayer.vertx.RequestHandler.GetHandler] or [de.eso.rxplayer.vertx.RequestHandler.PostHandler]
 * @param serializer a function which converts any object or a [List] or objects to a valid JSON string
 * @param deserializer a function which converts a valid JSON string to an object of a given Class
 */
class VertxServer(
        private val port: Int,
        private val handlers: List<RequestHandler>,
        private val serializer: (Any) -> String,
        private val deserializer: (Class<out Any>, String) -> Any
) {
    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val wsRequestMoshi = moshi.adapter<WebsocketRequest>(Types.getRawType(WebsocketRequest::class.java))

    fun listen() {
        ServiceHelper.loadFactory(VertxFactory::class.java)
                .vertx()
                .createHttpServer()
                .requestHandler { request: HttpServerRequest ->
                    val uri = request.uri()
                    val method = request.method().toString()
                    val handler: RequestHandler? = handlerFor(uri, method)

                    val responseObservable: Observable<out Any> = when (handler) {
                        null -> Observable.just("No handler defined for $method $uri")
                        else -> when (handler) {
                            is RequestHandler.PostHandler<*> -> Single
                                    .create<String> { emitter ->
                                        request.bodyHandler { bodyBuffer ->
                                            // TODO check if legal
                                            val body = bodyBuffer.getString(0, bodyBuffer.length())
                                            emitter.onSuccess(body)
                                        }
                                    }
                                    .map { body -> deserializer(handler.payloadClass, body) }
                                    .flatMap { payload: Any ->
                                        (handler as RequestHandler.PostHandler<Any>).handlerFunction(uri, payload)
                                    }.toObservable()
                            is RequestHandler.GetHandler -> {
                                val params = request.params().names()
                                        .map { it to request.params().getAll(it).first() }
                                        .toMap()
                                handler.handlerFunction(uri, params)
                            }
                        }
                    }

                    responseObservable
                            .map { response -> serializer(response) }
                            .firstOrError()
                            .subscribe({ json: String ->
                                request
                                        .response()
                                        .setStatusCode(200)
                                        .setStatusMessage("OK")
                                        .putHeader("content-type", "application/json; charset=utf-8")
                                        .putHeader("Access-Control-Allow-Origin", "*")
                                        .putHeader("Access-Control-Allow-Headers", "content-type")
                                        .end(json)
                            }, { error ->
                                request
                                        .response()
                                        .setStatusCode(500)
                                        .setStatusMessage("FAIL")
                                        .putHeader("content-type", "text/plain; charset=utf-8")
                                        .end(error.message)
                            })

                }.websocketHandler { webSocket ->
                    val disposable = CompositeDisposable()
                    webSocket.frameHandler { frame ->
                        val wsMessage: WebsocketRequest = wsRequestMoshi.fromJson(frame.textData())!!
                        if (wsMessage.subscribe) {
                            val uri = wsMessage.uri.replace("\\?.*$".toRegex(), "")
                            val handlerFor = handlerFor(uri, "GET")!! as RequestHandler.GetHandler
                            handlerFor.handlerFunction(uri, emptyMap())
                                    .map { serializer(it) }
                                    // data class WsMessage<T>(val id: String, val payload: T)
                                    .map {
                                        """
                                        {
                                            "id": "${wsMessage.id}",
                                            "payload": $it
                                        }
                                    """.trimIndent()
                                    }
                                    .subscribe({ text ->
                                        webSocket.writeFinalTextFrame(text)
                                    }, { e ->
                                        e.printStackTrace()
                                    }, {
                                        println("$wsMessage completed")
                                    }
                                    )
                                    .let { disposable.add(it) }
                        } else {
                            TODO()
                        }

                        webSocket.closeHandler { x -> disposable.dispose() }
                    }
                }.listen(port)
    }

    private fun handlerFor(uri: String, method: String): RequestHandler? {
        return handlers
                .filter { handler -> if (method == "GET") handler is RequestHandler.GetHandler else handler is RequestHandler.PostHandler<*> }
                .find { handler -> handler.predicate(uri) }
    }
}
