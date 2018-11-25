package de.eso.rxplayer.vertx

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.eso.rxplayer.Entertainment
import de.eso.rxplayer.EntertainmentService
import de.eso.rxplayer.Track
import io.reactivex.schedulers.Schedulers
import io.vertx.core.ServiceHelper
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.spi.VertxFactory

object Launcher {

    @JvmStatic
    fun main(args: Array<String>) {

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val trackMoshi = moshi.adapter<List<Track>>(Types.newParameterizedType(List::class.java, Track::class.java))

        val entertainment: Entertainment = EntertainmentService(scheduler = Schedulers.single())

        ServiceHelper.loadFactory(VertxFactory::class.java)
                .vertx()
                .createHttpServer()
                .requestHandler { request: HttpServerRequest ->
                    entertainment.usb.list()
                            .firstOrError()
                            .subscribe({ list ->
                                request
                                        .response()
                                        .setStatusCode(400)
                                        .setStatusMessage("OK")
                                        .putHeader("content-type", "application/json; charset=utf-8")
                                        .end(trackMoshi.indent("  ").toJson(list))
                            }, { error ->
                                request
                                        .response()
                                        .setStatusCode(500)
                                        .setStatusMessage("FAIL")
                                        .putHeader("content-type", "text/plain; charset=utf-8")
                                        .end(error.message)
                            })

                }
                .listen(7780)

        println("Listening on port 7780")
    }
}
