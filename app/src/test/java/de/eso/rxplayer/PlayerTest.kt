package de.eso.rxplayer

import io.reactivex.Observable
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.BehaviorSubject
import org.junit.Test
import java.util.concurrent.TimeUnit


class PlayerTest {
    private class AudioMock : Audio by proxy(Audio::class.java) {
        val state = BehaviorSubject.createDefault(Audio.AudioState.STOPPED)

        override fun observe(connection: Audio.Connection): Observable<Audio.AudioState> {
            return state
        }
    }

    private val scheduler = TestScheduler()
    private val audio = AudioMock()
    private val player = PlayerImpl(scheduler, audio, Audio.Connection.USB)

    @Test
    fun `tracks are available after a delay`() {
        val listTest = player.list().test()
        listTest.assertNoValues()

        scheduler.advanceTimeBy(500, TimeUnit.MILLISECONDS)

        listTest.assertNoErrors()
                .assertValueCount(1)
                .assertValue { list -> list.size == 600 }
    }

    @Test
    fun `initial state is stopped`() {
        player.isPlaying().test().assertValue(false)
    }

    @Test
    fun `starts playback after a delay if audio is started`() {
        // given
        player.isPlaying().test().assertValue(false)
        audio.state.onNext(Audio.AudioState.STARTED)
        // when
        val playTest = player.play().test()
        playTest.assertNoErrors()
        scheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS)
        // verify
        playTest.assertComplete()
        player.isPlaying().test().assertValue(true)
    }

    @Test
    fun `reports an error if audio is not started`() {
        // given
        player.isPlaying().test().assertValue(false)
        audio.state.onNext(Audio.AudioState.STARTING)
        // when
        val playTest = player.play().test()
        scheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS)
        // verify
        playTest.assertError { true }
        player.isPlaying().test().assertValue(false)
    }

    @Test
    fun `reports an error if playback is started twice`() {
        // given
        player.isPlaying().test().assertValue(false)
        audio.state.onNext(Audio.AudioState.STARTED)
        // when
        val playTest1 = player.play().test()
        scheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS)
        val playTest2 = player.play().test()
        scheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS)
        // verify
        playTest1.assertComplete().assertNoErrors()
        playTest2.assertError { true }
        player.isPlaying().test().assertValue(true)
    }

    @Test
    fun `stops playback after a delay`() {
        // given
        player.isPlaying().test().assertValue(false)
        audio.state.onNext(Audio.AudioState.STARTED)
        player.play().test()
        scheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS)
        // when
        val pauseTest = player.pause().test()
        scheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS)
        // verify
        pauseTest.assertComplete()
        player.isPlaying().test().assertValue(false)
    }

    @Test
    fun `selection changes track index after a delay`() {
        // given
        val nowPlaying = player.nowPlaying().doOnNext { println(it) }.test()
        nowPlaying.assertValue { it == 0 }
        // when
        val selectTest = player.select(100).test()
        scheduler.advanceTimeBy(1000, TimeUnit.MILLISECONDS)
        // verify
        selectTest.assertComplete()
        nowPlaying.assertValueAt(1, 100)
    }
}

