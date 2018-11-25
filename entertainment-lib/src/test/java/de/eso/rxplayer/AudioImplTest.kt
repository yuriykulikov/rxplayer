package de.eso.rxplayer

import io.reactivex.schedulers.TestScheduler
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class AudioImplTest {
    private val scheduler = TestScheduler()
    private val audio = AudioImpl(scheduler)

    @Test
    fun `all connections are initially stopped`() {
        Audio.Connection.values()
                .map { audio.observe(it).blockingFirst() }
                .let { assertThat(it) }
                .containsOnly(Audio.AudioState.STOPPED)
    }
}
