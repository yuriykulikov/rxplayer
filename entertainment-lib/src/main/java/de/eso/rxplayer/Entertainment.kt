package de.eso.rxplayer

import de.eso.rxplayer.Audio.AudioState
import de.eso.rxplayer.Audio.Connection
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single


/**
 * A facade for a simple entertainment player.
 */
interface Entertainment {
    /** Control audio routing, volume, etc. */
    val audio: Audio
    /** USB player. Has large lists and probably fast times. */
    val usb: Player
    /** CD Player. Has small lists ans slow reaction times. */
    val cd: Player
    /** Radio tuner. Station can be chosen, but the track cannot be. */
    val fm: Radio
    /** Browser for fetching artsists and albums */
    val browser: Browser
    /** Represents the audio system speakers. For testing and debugging purposes. */
    val speaker: Speaker
}

/**
 * Control and observe the [AudioState] of audio [Connection]s.
 *
 * Audio [Connection]
 *
 * An audio [Connection] represents an audio stream from a device (radio or encoder) to the physical
 * audio speaker. Each connection has a state. Only one [Connection] can be in the state other than
 * [AudioState.STOPPED] at any given moment. [Connection] state transitions have to be triggered
 * manually.
 *
 * Queueing of any kind is not implemented. A system will malfunction in case of an attempt to start
 * multiple connections simultaneously. A state of a [Connection] can be queried by calling the
 * [observe] function.
 *
 * [fadeId] and [fadeOut]
 *
 * Every connection starts at the lowest volume. Function [fadeId] has to be used to gradually fade
 * in the volume. [fadeOut] can be used to decrease the volume to zero before stopping. Only a
 * [AudioState.STARTED] can be fade in or out.
 */
interface Audio {
    enum class Connection { USB, CD, RADIO; }
    enum class AudioState { STOPPED, STARTING, STARTED, STOPPING; }

    /** Triggers a start of a connection. Connection immediately enters [AudioState.STARTING] and
     * will enter [AudioState.STARTED] after a while. Can only be called if the [Connection] is
     * in [AudioState.STOPPED] state. */
    fun start(connection: Connection)

    /** Triggers a stop of a connection. Connection immediately enters [AudioState.STOPPING] and
     * will enter [AudioState.STOPPED] after a while. Can only be called if the [Connection] is
     * in [AudioState.STARTED] state. */
    fun stop(connection: Connection)

    /** Gradually ramps up the volume of a [AudioState.STARTED] connection. */
    fun fadeIn(connection: Connection): Completable

    /** Gradually ramps down the volume of a [AudioState.STARTED] connection. */
    fun fadeOut(connection: Connection): Completable

    /** Observe the state of a given connection. Any exceptions which may occur when using [start]
     * or [stop] will be reported via this observable. Recovery is not possible. */
    fun observe(connection: Connection): Observable<AudioState>
}

/**
 * Media player instance. Each player can be individually controlled. Multiple players can be running
 * simultaneously, but each playing player requires a working audio sink (a corresponding
 * [Audio.Connection] must be [Audio.AudioState.STARTED]. A player may report an error if it is unable
 * to write into the sink.
 *
 * Queueing of any kind is not implemented. A system may malfunction or enter undefined state of a
 * request is sent before previous request has been responded.
 */
interface Player {
    /** A list of tracks available on this player */
    fun list(): Observable<List<Track>>

    /** Index of a track in the [list] which is currently playing */
    fun nowPlaying(): Observable<Int>

    /** Whether the player is playing or not */
    fun isPlaying(): Observable<Boolean>

    /** Resumes the playback. Completable is lazy and must be subscribed. */
    fun play(): Completable

    /** Pauses the playback. Completable is lazy and must be subscribed. */
    fun pause(): Completable

    /** Selects a track for playback. Completable is lazy and must be subscribed. */
    fun select(index: Int): Completable

    /** Name of this player for logging */
    val name: String
}

/**
 * Radio tuner instance. Radio does not require a working audio sink (unlike a player).
 *
 * Queueing of any kind is not implemented. A system may malfunction or enter undefined state of a
 * request is sent before previous request has been responded.
 */
interface Radio {
    /** List of radio stations currently available */
    fun list(): Observable<List<Station>>

    /** Index of the station which is tuned */
    fun nowPlaying(): Observable<Int>

    /** Track which is playing */
    fun radioText(): Observable<Track>

    /** Select a station from the list */
    fun select(index: Int): Completable
}

/**
 * Access to the Media database. Artists and albums can be queried by the id.
 */
interface Browser {
    /** Fetches the album for the given id. Always gives the same result. Does not implement caching. */
    fun albumById(id: Int): Single<Album>

    /** Fetches the artist for the given id. Always gives the same result. Does not implement caching. */
    fun artistBy(id: Int): Single<Artist>

    /** Fetches all artists */
    fun allArtists(): Single<List<Artist>>

    /** Fetches all albums */
    fun allAlbums(): Single<List<Album>>
}

/**
 * Represents a physical speaker. Can be used to make assertions about the system state in tests.
 */
interface Speaker {
    /** What is currently on */
    fun observe(): Observable<SpeakerState>
}

data class Station(val name: String, val logo: String)

data class Track(
        val id: Int,
        val albumId: Int,
        val artistId: Int,
        val title: String,
        val duration: Int
        // val createdAt: String,
        // val updatedAt: String
)

data class Artist(
        val name: String,
        // val createdAt: String,
        // val updatedAt: String,
        val id: Int
)

data class Album(
        val id: Int,
        val name: String,
        val artistId: Int,
        val cover: String,
        val coverSmall: String,
        val coverMedium: String,
        val coverBig: String,
        val coverXl: String
        // val createdAt: String,
        // val updatedAt: String
)

data class SpeakerState(
        val track: Track?,
        val audioConnection: Audio.Connection?,
        val volume: Float?
)