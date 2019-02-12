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
import java.util.*
import java.util.concurrent.TimeUnit


/**
 * Implementation of [Entertainment], facade for multiple subsystems:
 * * [audio]
 * * [usb]
 * * [cd]
 * * [fm]
 */
class EntertainmentService(scheduler: Scheduler, hardDifficulty: Boolean = true) : Entertainment {
    init {
        // TODO check that scheduler is single-threaded
    }

    override val audio: Audio = AudioImpl(scheduler)
    override val usb: Player = PlayerImpl(
            scheduler = scheduler,
            audio = audio,
            checkAudio = hardDifficulty,
            connection = Audio.Connection.USB,
            name = "usb"
    )
    override val cd: Player = PlayerImpl(
            scheduler = scheduler,
            audio = audio,
            checkAudio = hardDifficulty,
            connection = Audio.Connection.CD,
            name = "sd"
    )
    override val fm: Radio = RadioImpl(
            scheduler = scheduler,
            audio = audio
    )
    override val speaker: Speaker = SpeakerImpl(audio, usb, cd, fm)
    override val browser: Browser = BrowserImpl(scheduler)
}

class SpeakerImpl(audio: Audio, usb: Player, cd: Player, fm: Radio) : Speaker {
    override fun observe(): Observable<SpeakerState> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class RadioImpl(private val scheduler: Scheduler, audio: Audio) : Radio {
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val stations = moshi.readList(Station::class.java, "stations.json")
    private val currentStationIndex: BehaviorSubject<Int> = BehaviorSubject.createDefault(0)

    private val currentTrack: Observable<Track>

    private val trackList: List<Track> by lazy {
        moshi.readMap(Track::class.java, "tracks.json")
                .values
                .toList()
    }

    init {
        val random = Random()
        currentTrack = this.currentStationIndex.switchMap {
            Observable.interval(0, 10, TimeUnit.SECONDS, scheduler)
                    .map {
                        val nextTrackIndex = random.nextInt(trackList.size - 1)
                        trackList[nextTrackIndex]
                    }
        }
                .replay(1)
                .autoConnect(-1)

    }

    override fun list(): Observable<List<Station>> {
        return Observable.just(stations)
    }

    override fun nowPlaying(): Observable<Int> {
        return currentStationIndex.observeOn(scheduler)
    }

    override fun radioText(): Observable<Track> {
        return currentTrack
    }

    override fun select(index: Int): Completable = Completable.defer {
        this.currentStationIndex.onNext(index)
        Completable.complete()
    }.subscribeOn(scheduler)
}

class PlayerImpl(
        /** Delays emissions and protects against multithreaded access */
        private val scheduler: Scheduler,
        /** Used to check if we can actually stream */
        private val audio: Audio,
        private val connection: Audio.Connection,
        override val name: String,
        private val checkAudio: Boolean = false,
        private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
        /** delays emissions by this time in milliseconds */
        private val delay: Long = 500
) : Player {
    private val activeTrackIndex: BehaviorSubject<Int> = BehaviorSubject.createDefault(0)
    private val isPlaying: BehaviorSubject<Boolean> = BehaviorSubject.createDefault(false)
    private val trackList: List<Track> by lazy {
        val allTracks = moshi.readMap(Track::class.java, "tracks.json")
                .values
                .toList()
        if (name == "sd") allTracks.subList(400, 450) else allTracks
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
                        checkAudio && isPlaying.value == true -> Completable.error(IllegalStateException("[PlayerImpl.play] Can't resume because already playing"))
                        // developer has screwed up: audio connection was not started and we cannot stream
                        checkAudio && audioState != Audio.AudioState.STARTED -> Completable.error(IllegalStateException("[PlayerImpl.play] Can't steam because $connection is $audioState"))
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

    override fun allArtists(): Single<List<Artist>> {
        return Single.fromCallable { artists.values.toList().sortedBy { it.id } }
    }

    override fun allAlbums(): Single<List<Album>> {
        return Single.fromCallable { albums.values.toList().sortedBy { it.id } }
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

/**
 * Read the resource and parse the content into a list.
 */
fun <T> Moshi.readList(type: Class<T>, resource: String): List<T> {
    val listType = Types.newParameterizedType(
            List::class.java,
            type
    )
    return this.adapter<List<T>>(listType)
            .fromJson(this.javaClass.classLoader.getResource(resource).readText())
            ?: emptyList()
}


private fun Scheduler.timer(delay: Long = 500, action: () -> Unit): Completable {
    return Completable
            .timer(delay, TimeUnit.MILLISECONDS, this)
            .andThen(Completable.defer {
                action()
                Completable.complete()
            })
}