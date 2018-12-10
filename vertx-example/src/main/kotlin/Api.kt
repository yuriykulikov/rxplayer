package de.eso.rxplayer.vertx

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.eso.rxplayer.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest


class ApiAdapter(private val entertainment: Entertainment, moshi: Moshi) {
    val tracksMoshi = moshi.adapter<List<Track>>(Types.newParameterizedType(List::class.java, Track::class.java))
    val playersMoshi = moshi.adapter<List<PlayerData>>(Types.newParameterizedType(List::class.java, PlayerData::class.java))
    val playerMoshi = moshi.adapter<PlayerData>(Types.getRawType(PlayerData::class.java))
    val methodMoshi = moshi.adapter<Method>(Types.getRawType(Method::class.java))
    val methodsDescMoshi = moshi.adapter<List<MethodDesc>>(Types.newParameterizedType(List::class.java, MethodDesc::class.java))
    val tunersMoshi = moshi.adapter<List<TunerData>>(Types.newParameterizedType(List::class.java, TunerData::class.java))
    val tunerMoshi = moshi.adapter<TunerData>(Types.getRawType(TunerData::class.java))
    val stationsMoshi = moshi.adapter<List<Station>>(Types.newParameterizedType(List::class.java, Station::class.java))

    fun flow(request: Request): Observable<String> {
        val params = request.params
        return when {
            request.uri.isEmpty() || request.uri == "/" -> {
                Observable.just("""{"resources": ["/players", "/tuners"] }""")
            }
            request.uri == "/players" -> {
                Observables
                        .combineLatest(flowPlayer(entertainment.usb), flowPlayer(entertainment.cd)) { usb, sd -> listOf(usb, sd) }
                        .map { playersMoshi.indent("  ").toJson(it) }
            }
            request.uri == "/players/sd" -> flowPlayer(entertainment.cd).map { playerMoshi.indent("  ").toJson(it) }
            request.uri == "/players/usb" -> flowPlayer(entertainment.usb).map { playerMoshi.indent("  ").toJson(it) }
            request.uri == "/players/usb/tracks" -> flowTracks(entertainment.usb, params)
            request.uri == "/players/sd/tracks" -> flowTracks(entertainment.cd, params)
            request.uri == "/players/usb/rpc" && request.method != null -> handleMethod(entertainment.usb, request.method)
            request.uri == "/players/sd/rpc" && request.method != null -> handleMethod(entertainment.cd, request.method)
            request.uri == "/players/usb/rpc" -> showMethods(entertainment.usb)
            request.uri == "/players/sd/rpc" -> showMethods(entertainment.usb)

            request.uri == "/tuners" -> {
                flowTuner(entertainment.fm)
                        .map { listOf(it) }
                        .map { tunersMoshi.indent("  ").toJson(it) }
            }

            request.uri == "/tuners/fm" -> {
                flowTuner(entertainment.fm)
                        .map { tunerMoshi.indent("  ").toJson(it) }
            }

            request.uri == "/tuners/fm/stations" -> entertainment.fm.list().map { list -> stationsMoshi.toJson(list) }
            request.uri == "/tuners/fm/rpc" && request.method != null -> handleTunerMethod(entertainment.fm, request.method)
            request.uri == "/tuners/fm/rpc" -> showTunerMethods(entertainment.fm)

            else -> Observable.just("""{"error": "${request.uri} cannot be handled"}""")
        }
    }

    data class TunerData(
            val stations: String,
            val rpc: String,
            val radioText: Track,
            val stationIndex: Int,
            val station: Station
    )

    private fun handleMethod(player: Player, method: Method): Observable<String> {
        return when {
            method.method == "select" -> {
                player.select(method.params["index"]?.let { it as Number }?.toInt() ?: 0)
                        .andThen(Observable.defer { flowPlayer(player) })
                        .take(1)
                        .map { playerMoshi.indent("  ").toJson(it) }
            }
            method.method == "play" -> {
                player.play()
                        .andThen(Observable.defer { flowPlayer(player) })
                        .take(1)
                        .map { playerMoshi.indent("  ").toJson(it) }
            }
            method.method == "pause" -> {
                player.pause()
                        .andThen(Observable.defer { flowPlayer(player) })
                        .take(1)
                        .map { playerMoshi.indent("  ").toJson(it) }
            }
            else -> Observable.error(IllegalArgumentException("Cannot interpret ${methodMoshi.toJson(method)}"))
        }
    }

    private fun showMethods(player: Player): Observable<String> {
        return Observable.just(
                listOf(
                        MethodDesc(name = "select",
                                uri = "/players/${player.name}/rpc",
                                verb = "POST",
                                example = Method(method = "select", params = mapOf("index" to 1))
                        ),
                        MethodDesc(name = "play",
                                uri = "/players/${player.name}/rpc",
                                verb = "POST",
                                example = Method(method = "play", params = mapOf())
                        ),
                        MethodDesc(name = "pause",
                                uri = "/players/${player.name}/rpc",
                                verb = "POST",
                                example = Method(method = "pause", params = mapOf())
                        )
                ))
                .map { methodsDescMoshi.toJson(it) }
    }


    private fun flowTracks(player: Player, params: Map<String, String>): Observable<String> {
        return player.list()
                .map { list ->
                    list.subList(params["from"]?.toInt() ?: 0, params["to"]?.toInt() ?: list.size)
                }
                .map { list -> tracksMoshi.toJson(list) }
    }

    private fun flowPlayer(player: Player): Observable<PlayerData> {
        val playing: Observable<Boolean> = player.isPlaying()
        val nowPlaying: Observable<Int> = player.nowPlaying()
        val obsList: Observable<List<Track>> = player.list()

        return Observables
                .combineLatest(playing, nowPlaying, obsList) { isPlaying, index, list ->
                    PlayerData(isPlaying = isPlaying,
                            nowPlayingIndex = index,
                            duration = list[index].duration,
                            nowPlaying = list[index],
                            methods = "/players/${player.name}/methods",
                            tracks = "/players/${player.name}/tracks"
                    )
                }
    }

    private fun flowTuner(radio: Radio): Observable<TunerData> {
        return Observables.combineLatest(radio.nowPlaying(), radio.radioText(), radio.list()) { index, radioText, stations ->
            TunerData(
                    stations = "/tuners/fm/stations",
                    rpc = "/tuners/fm/rpc",
                    radioText = radioText,
                    stationIndex = index,
                    station = stations[index]
            )
        }
    }

    private fun handleTunerMethod(radio: Radio, method: Method): Observable<String> {
        return when {
            method.method == "select" -> {
                radio.select(method.params["index"]?.let { it as Number }?.toInt() ?: 0)
                        .andThen(Observable.defer { flowTuner(radio) })
                        .take(1)
                        .map { tunerMoshi.indent("  ").toJson(it) }
            }
            else -> Observable.error(IllegalArgumentException("Cannot interpret ${methodMoshi.toJson(method)}"))
        }
    }

    private fun showTunerMethods(fm: Radio): Observable<String> {
        return Observable.just(
                listOf(
                        MethodDesc(name = "select",
                                uri = "/tuners/fm/rpc",
                                verb = "POST",
                                example = Method(method = "select", params = mapOf("index" to 1))
                        )
                ))
                .map { methodsDescMoshi.toJson(it) }
    }

    fun sessionFrom(request: HttpServerRequest): Single<Request> {
        val params = request.params().names()
                .map { it to request.params().getAll(it).first() }
                .toMap()

        return if (request.method() == HttpMethod.GET || request.method() == HttpMethod.OPTIONS) {
            Single.just(Request(
                    uri = request.uri().replace("\\?.*$".toRegex(), ""),
                    verb = request.method().toString(),
                    params = params))
        } else {
            Single.create<Buffer> { emitter -> request.bodyHandler { emitter.onSuccess(it) } }
                    .map { bodyBuffer ->
                        val bodyString = bodyBuffer.getString(0, bodyBuffer.length())
                        Request(
                                uri = request.uri().replace("\\?.*$".toRegex(), ""),
                                verb = request.method().toString(),
                                params = params,
                                method = methodMoshi.fromJson(bodyString))
                    }
        }
    }
}

data class Request(val uri: String, val verb: String, val params: Map<String, String>, val method: Method? = null)

data class PlayerData(
        val isPlaying: Boolean,
        val nowPlayingIndex: Int,
        val nowPlaying: Track,
        val duration: Int,
        val methods: String,
        val tracks: String
)

data class MethodDesc(val name: String, val uri: String, val verb: String, val example: Method)
data class Method(val method: String, val params: Map<String, Any>)