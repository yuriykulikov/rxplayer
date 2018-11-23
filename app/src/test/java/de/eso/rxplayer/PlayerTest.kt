package de.eso.rxplayer

import io.reactivex.schedulers.TestScheduler
import org.junit.Test
import io.reactivex.subscribers.TestSubscriber


class PlayerTest {
    val scheduler = TestScheduler()
    val audio = AudioImpl(scheduler)
    val player = PlayerImpl(scheduler, audio)

    @Test
    fun `startStopTest`() {
        val subscriber = TestSubscriber<Audio.AudioState>()
        player.play().andThen(player.pause())
        val obs = audio.observe(Audio.Connection.RADIO)
        obs.subscribe{subscriber}

        subscriber.assertNoErrors()
        subscriber.assertValueCount(2)

    }


    @Test
    fun `selectTest`() {
        val selectIndex = 15
        var disp = player.nowPlaying().subscribe {next -> assert(next == -1) }
        disp.dispose()
        player.select(selectIndex)

        disp = player.nowPlaying().subscribe { next -> assert(next == selectIndex) }
        disp.dispose()
    }
}