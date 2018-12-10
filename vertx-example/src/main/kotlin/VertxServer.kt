package de.eso.rxplayer.vertx

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.reactivex.disposables.CompositeDisposable
import io.vertx.core.ServiceHelper
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.spi.VertxFactory

class VertxServer(private val port: Int, private val adapter: ApiAdapter) {
    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    val wsRequestMoshi = moshi.adapter<WsRequest>(Types.getRawType(WsRequest::class.java))

    val disposable = CompositeDisposable()

    fun listen() {
        ServiceHelper.loadFactory(VertxFactory::class.java)
                .vertx()
                .createHttpServer()
                .requestHandler { request: HttpServerRequest ->
                    adapter.sessionFrom(request)
                            .flatMapObservable { adapter.flow(it) }
                            .firstOrError()
                            .subscribe({ json: String ->
                                request
                                        .response()
                                        .setStatusCode(400)
                                        .setStatusMessage("OK")
                                        .putHeader("content-type", "application/json; charset=utf-8")
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
                    webSocket.frameHandler { frame ->
                        val wsMessage: WsRequest = wsRequestMoshi.fromJson(frame.textData())!!

                        if (wsMessage.subscribe) {
                            val request = Request(uri = wsMessage.uri!!.replace("\\?.*$".toRegex(), ""),
                                    verb = "GET",
                                    params = mapOf(),
                                    method = null)

                            adapter.flow(request)
                                    // data class WsMessage<T>(val id: String, val payload: T)
                                    .map {
                                        """
                                        {
                                            "id": "${wsMessage.id}",
                                            "payload": $it
                                        }
                                    """.trimIndent()
                                    }
                                    .subscribe { text ->
                                        webSocket.writeFinalTextFrame(text)
                                    }
                                    .let { disposable.add(it) }
                        } else {
                            TODO()
                        }

                        webSocket.closeHandler { x -> disposable.dispose() }
                    }
                }.listen(port)
    }
}