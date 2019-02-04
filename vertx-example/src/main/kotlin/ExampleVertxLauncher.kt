package de.eso.rxplayer.vertx

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.eso.rxplayer.Entertainment
import de.eso.rxplayer.EntertainmentService
import io.reactivex.schedulers.Schedulers

/**
 * Run this with ./gradlew vertx-example:run
 */
object ExampleVertxLauncher {

    @JvmStatic
    fun main(args: Array<String>) {

        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

        val entertainment: Entertainment = EntertainmentService(scheduler = Schedulers.single())

        val adapter = ExampleVertxAdapter(entertainment)

        VertxServer(
                7780,
                adapter.handlers(),
                serializer = moshi.serializer(),
                deserializer = moshi.deserializer()
        ).listen()
    }
}

