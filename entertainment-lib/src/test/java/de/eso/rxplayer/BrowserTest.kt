package de.eso.rxplayer

import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import org.junit.Test
import java.util.concurrent.TimeUnit

class BrowserTest {
    private class AudioMock : Audio by proxy(Audio::class.java)

    private val scheduler = TestScheduler()
    private val player = PlayerImpl(scheduler, AudioMock(), Audio.Connection.USB)
    private val browser: Browser = BrowserImpl(scheduler)

    @Test
    fun `all tracks have valid albums`() {
        val albumsTest = player.list()
                .firstOrError()
                .flatMapObservable { Observable.fromIterable(it) }
                .map { it.albumId }
                .distinct()
                .flatMapSingle { albumId -> browser.albumById(albumId) }
                .toList()
                .test()

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS)

        albumsTest.assertValue { it.size == 195 }
    }

    @Test
    fun `all tracks have valid artists`() {
        val artistsTest = player.list()
                .firstOrError()
                .flatMapObservable { Observable.fromIterable(it) }
                .map { it.artistId }
                .distinct()
                .flatMapSingle { id -> browser.artistBy(id) }
                .toList()
                .test()

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS)

        artistsTest.assertValue { it.size == 6 }
    }

    @Test
    fun `all albums have valid artists`() {
        val artistsTest = player.list()
                .firstOrError()
                .flatMapObservable { Observable.fromIterable(it) }
                .map { it.albumId }
                .distinct()
                .flatMapSingle { id -> browser.albumById(id) }
                .map { it.artistId }
                .distinct()
                .flatMapSingle { id -> browser.artistBy(id) }
                .toList()
                .test()

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS)

        artistsTest.assertValue { it.size == 6 }
    }
}

