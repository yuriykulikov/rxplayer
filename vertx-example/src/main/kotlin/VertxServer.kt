package de.eso.rxplayer.vertx

import io.vertx.core.ServiceHelper
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.spi.VertxFactory

class VertxServer(private val port: Int, private val adapter: ApiAdapter) {
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

                }
                .listen(port)
    }
}