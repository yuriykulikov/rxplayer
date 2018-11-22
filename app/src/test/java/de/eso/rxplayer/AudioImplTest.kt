package de.eso.rxplayer

import io.reactivex.schedulers.TestScheduler
import org.assertj.core.api.AbstractListAssert
import org.assertj.core.api.Assertions
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class AudioImplTest {
    val scheduler = TestScheduler()
    val audio = AudioImpl(scheduler)

    @Test
    fun `all connections are initially stopped`() {
        Audio.Connection.values()
                .map { audio.observe(it).blockingFirst() }
                .assertThatIt()
                .containsOnly(Audio.AudioState.STOPPED)
    }

    fun <T> List<T>.assertThatIt(): AbstractListAssert<*, out MutableList<out T>, T> {
        return Assertions.assertThat(this)
    }
}
