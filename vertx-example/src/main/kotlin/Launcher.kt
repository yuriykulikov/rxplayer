package de.eso.rxplayer.vertx

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.eso.rxplayer.Entertainment
import de.eso.rxplayer.EntertainmentService
import io.reactivex.schedulers.Schedulers

object Launcher {

    @JvmStatic
    fun main(args: Array<String>) {

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val entertainment: Entertainment = EntertainmentService(scheduler = Schedulers.single())

        val adapter: ApiAdapter = ApiAdapter(entertainment, moshi)

        VertxServer(7780, adapter).listen()
    }
}
