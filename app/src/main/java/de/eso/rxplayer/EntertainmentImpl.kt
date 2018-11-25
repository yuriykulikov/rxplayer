package de.eso.rxplayer

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import io.reactivex.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit


class EntertainmentService(scheduler: Scheduler) : Entertainment {
    init {
        // TODO check that scheduler is single-threaded
    }

    override val audio: Audio = AudioImpl(scheduler)
    override val usb: Player = PlayerImpl(scheduler, audio, Audio.Connection.USB)
    override val cd: Player = PlayerImpl(scheduler, audio, Audio.Connection.CD)
    override val fm: Radio = RadioImpl(scheduler, audio)
    override val speaker: Speaker = SpeakerImpl(audio, usb, cd, fm)
    override val browser: Browser = BrowserImpl(scheduler)
}

class SpeakerImpl(audio: Audio, usb: Player, cd: Player, fm: Radio) : Speaker {
    override fun observe(): Observable<SpeakerState> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class RadioImpl(private val scheduler: Scheduler, audio: Audio) : Radio {
    override fun list(): Observable<List<Station>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun nowPlaying(): Observable<Int> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun radioText(): Observable<Track> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun select(index: Int): Completable {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class PlayerImpl(
        /** Delays emissions and protects against multithreaded access */
        private val scheduler: Scheduler,
        /** Used to check if we can actually stream */
        private val audio: Audio,
        private val connection: Audio.Connection,
        private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
        /** delays emissions by this time in milliseconds */
        private val delay: Long = 500
) : Player {
    private val activeTrackIndex: BehaviorSubject<Int> = BehaviorSubject.createDefault(0)
    private val isPlaying: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(false)
    private val trackList: List<Track> by lazy {
        moshi.readMap(Track::class.java, "tracks.json")
                .values
                .toList()
    }

    override fun nowPlaying(): Observable<Int> {
        return activeTrackIndex.hide()
    }

    override fun isPlaying(): Observable<Boolean> {
        return isPlaying.hide()
    }

    /**
     * Checks if we can stream and starts a timer to play if we can actually stream
     */
    override fun play() = Completable.defer {
        // we have to verify audio connection state
        audio.observe(connection)
                .firstOrError()
                .flatMapCompletable { audioState ->
                    when {
                        isPlaying.value == true -> Completable.error(IllegalStateException("[PlayerImpl.play] Can't resume because already playing"))
                        // developer has screwed up: audio connection was not started and we cannot stream
                        audioState != Audio.AudioState.STARTED -> Completable.error(IllegalStateException("[PlayerImpl.play] Can't steam because $connection is $audioState"))
                        // everything fine, we can stream, so let's resume playback after "loading"
                        else -> scheduler.timer(delay) { isPlaying.onNext(true) }
                    }
                }
    }.subscribeOn(scheduler)

    override fun pause() = Completable.defer {
        when {
            isPlaying.value == false -> Completable.error(IllegalStateException("[PlayerImpl.play] Can't pause because paused playing"))
            else -> scheduler.timer(delay) { isPlaying.onNext(false) }
        }
    }.subscribeOn(scheduler)

    override fun select(index: Int) = Completable.defer {
        scheduler.timer(delay) { activeTrackIndex.onNext(index) }
    }.subscribeOn(scheduler)

    override fun list(): Observable<List<Track>> {
        return Observable.just(trackList).delay(100, TimeUnit.MILLISECONDS, scheduler)
    }
}

class AudioImpl(private val scheduler: Scheduler) : Audio {
    private val usb: BehaviorSubject<Audio.AudioState> = BehaviorSubject.createDefault(Audio.AudioState.STOPPED)
    private val cd: BehaviorSubject<Audio.AudioState> = BehaviorSubject.createDefault(Audio.AudioState.STOPPED)
    private val radio: BehaviorSubject<Audio.AudioState> = BehaviorSubject.createDefault(Audio.AudioState.STOPPED)

    private var pending: Disposable = Disposables.empty()

    override fun observe(connection: Audio.Connection): Observable<Audio.AudioState> {
        return subjectFor(connection).hide()
    }

    private fun subjectFor(connection: Audio.Connection): BehaviorSubject<Audio.AudioState> {
        return when (connection) {
            Audio.Connection.USB -> usb
            Audio.Connection.CD -> cd
            Audio.Connection.RADIO -> radio
        }
    }

    override fun start(connection: Audio.Connection) {
        scheduler.scheduleDirect {
            val subject = subjectFor(connection)
            when {
                subject.value != Audio.AudioState.STOPPED -> {
                    subject.onError(IllegalStateException("[AudioImpl.start] Can't start $connection because it is ${subject.value}"))
                }
                listOf(usb, cd, radio).map { it.value }.all { it == Audio.AudioState.STOPPED } -> {
                    subject.onNext(Audio.AudioState.STARTING)

                    pending.dispose()
                    pending = Single.timer(1, TimeUnit.SECONDS, scheduler)
                            .subscribe { _ -> subject.onNext(Audio.AudioState.STARTED) }
                }
                else -> {
                    subject.onError(IllegalStateException("[AudioImpl.start] Can't start $connection because there are connections still running"))
                }
            }
        }
    }

    override fun stop(connection: Audio.Connection) {
        scheduler.scheduleDirect {
            val subject = subjectFor(connection)
            if (subject.value == Audio.AudioState.STARTING || subject.value == Audio.AudioState.STARTED) {
                subject.onNext(Audio.AudioState.STOPPING)

                pending.dispose()
                pending = Single.timer(1, TimeUnit.SECONDS, scheduler)
                        .subscribe { _ -> subject.onNext(Audio.AudioState.STOPPED) }
            } else {
                subject.onError(IllegalStateException("[AudioImpl.stop] Can't stop $connection because it is ${subject.value}"))
            }
        }
    }

    override fun fadeIn(connection: Audio.Connection): Completable {
        return Completable.defer {
            val subject = subjectFor(connection)
            return@defer when {
                subject.value == Audio.AudioState.STARTED -> Completable.timer(1, TimeUnit.MILLISECONDS, scheduler)
                else -> Completable.error(IllegalStateException("[AudioImpl.fadeIn] Can't fadeIn $connection because it is ${subject.value}"))
            }
        }.subscribeOn(scheduler)
    }

    override fun fadeOut(connection: Audio.Connection): Completable {
        return Completable.defer {
            val subject = subjectFor(connection)
            return@defer when {
                subject.value == Audio.AudioState.STARTED -> Completable.timer(1, TimeUnit.MILLISECONDS, scheduler)
                else -> Completable.error(IllegalStateException("[AudioImpl.fadeOut] Can't fadeOut $connection because it is ${subject.value}"))
            }
        }.subscribeOn(scheduler)
    }
}

/**
 * Performs a lookup in the map, delays the results by some time
 */
class BrowserImpl(
        /** scheduler for delayed emissions*/
        private val scheduler: Scheduler,
        /** delays emissions by this time in milliseconds */
        private val delay: Long = 100,
        private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) : Browser {
    private val artists: Map<Int, Artist> by lazy {
        moshi.readMap(Artist::class.java, "artists.json")
                .mapKeys { it.key.toInt() }
    }

    private val albums: Map<Int, Album> by lazy {
        moshi.readMap(Album::class.java, "albums.json")
                .mapKeys { it.key.toInt() }
    }

    override fun artistBy(id: Int): Single<Artist> {
        return artists[id]
                ?.let { Single.just(it).delay(delay, TimeUnit.MILLISECONDS, scheduler) }
                ?: Single.error(IllegalArgumentException("[BrowserImpl.artistBy] id $id is not valid."))
    }

    override fun albumById(id: Int): Single<Album> {
        return albums[id]
                ?.let { Single.just(it).delay(delay, TimeUnit.MILLISECONDS, scheduler) }
                ?: Single.error(IllegalArgumentException("[BrowserImpl.albumById] id $id is not valid."))
    }
}

/**
 * Read the resource and parse the content into a map.
 */
fun <T> Moshi.readMap(type: Class<T>, resource: String): Map<String, T> {
    val mapType = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            type
    )
    return this.adapter<Map<String, T>>(mapType)
            .fromJson(this.javaClass.classLoader.getResource(resource).readText())
            ?: emptyMap()
}

private fun Scheduler.timer(delay: Long = 500, action: () -> Unit): Completable {
    return Completable
            .timer(delay, TimeUnit.MILLISECONDS, this)
            .andThen(Completable.defer {
                action()
                Completable.complete()
            })
}