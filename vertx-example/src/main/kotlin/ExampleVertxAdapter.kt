package de.eso.rxplayer.vertx

import de.eso.rxplayer.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables


class ExampleVertxAdapter(val entertainment: Entertainment) {
    fun handlers(): List<RequestHandler> {
        return listOf(
                RequestHandler.GetHandler(
                        predicate = { uri -> uri.isEmpty() || uri == "/" },
                        handlerFunction = { uri, payload -> Observable.just("""{"resources": ["/players", "/tuners", "/albums", "/artists"] }""") }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/players" },
                        handlerFunction = { uri, payload ->
                            Observables.combineLatest(flowPlayer(entertainment.usb), flowPlayer(entertainment.cd)) { usb, sd -> listOf(usb, sd) }
                        }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/players/sd" },
                        handlerFunction = { uri, payload -> flowPlayer(entertainment.cd) }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/players/usb" },
                        handlerFunction = { uri, payload -> flowPlayer(entertainment.usb) }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri.startsWith("/players/usb/tracks") },
                        handlerFunction = { uri, params -> flowTracks(entertainment.usb, params) }
                )
                ,
                RequestHandler.GetHandler(
                        predicate = { uri -> uri.startsWith("/players/sd/tracks") },
                        handlerFunction = { uri, params -> flowTracks(entertainment.cd, params) }
                ),
                RequestHandler.PostHandler<Method>(
                        predicate = { uri -> uri == "/players/usb/rpc" },
                        payloadClass = Method::class.java,
                        handlerFunction = { uri, payload -> handleMethod(entertainment.usb, payload) }
                ),
                RequestHandler.PostHandler(
                        predicate = { uri -> uri == "/players/sd/rpc" },
                        payloadClass = Method::class.java,
                        handlerFunction = { uri, payload -> handleMethod(entertainment.cd, payload!!) }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/players/sd/rpc" },
                        handlerFunction = { uri, payload -> showMethods(entertainment.usb) }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/artists" },
                        handlerFunction = { uri, payload -> entertainment.browser.allArtists().toObservable() }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/albums" },
                        handlerFunction = { uri, payload -> entertainment.browser.allAlbums().toObservable() }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri.startsWith("/artists/") },
                        handlerFunction = { uri, payload -> entertainment.browser.artistBy(uri.removePrefix("/artists/").toInt()).toObservable() }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri.startsWith("/albums/") },
                        handlerFunction = { uri, payload -> entertainment.browser.albumById(uri.removePrefix("/albums/").toInt()).toObservable() }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/tuners" },
                        handlerFunction = { uri, payload -> flowTuner(entertainment.fm).map { listOf(it) } }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/tuners/fm" },
                        handlerFunction = { uri, payload -> flowTuner(entertainment.fm) }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/tuners/fm/stations" },
                        handlerFunction = { uri, payload -> entertainment.fm.list() }
                ),
                RequestHandler.PostHandler(
                        predicate = { uri -> uri == "/tuners/fm/rpc" },
                        payloadClass = Method::class.java,
                        handlerFunction = { uri, payload -> handleTunerMethod(entertainment.fm, payload) }
                ),
                RequestHandler.GetHandler(
                        predicate = { uri -> uri == "/tuners/fm/rpc" },
                        handlerFunction = { uri, payload -> showTunerMethods(entertainment.fm) }
                )
        )
    }

    data class TunerData(
            val stations: String,
            val rpc: String,
            val radioText: Track,
            val stationIndex: Int,
            val station: Station
    )

    private fun handleMethod(player: Player, method: Method): Single<PlayerData> {
        return when {
            method.method == "select" -> {
                player.select(method.params["index"]?.let { it as Number }?.toInt() ?: 0)
                        .andThen(Observable.defer { flowPlayer(player) })
                        .take(1)
            }
            method.method == "play" -> {
                player.play()
                        .andThen(Observable.defer { flowPlayer(player) })
                        .take(1)
            }
            method.method == "pause" -> {
                player.pause()
                        .andThen(Observable.defer { flowPlayer(player) })
                        .take(1)
            }
            else -> Observable.error(IllegalArgumentException("Cannot interpret $method"))
        }.firstOrError()
    }

    private fun showMethods(player: Player): Observable<List<MethodDesc>> {
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
    }


    private fun flowTracks(player: Player, params: Map<String, String>): Observable<List<Track>> {
        return player.list()
                .map { list ->
                    list.subList(params["from"]?.toInt() ?: 0, params["to"]?.toInt() ?: list.size)
                }
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
                            rpc = "/players/${player.name}/rpc",
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

    private fun handleTunerMethod(radio: Radio, method: Method): Single<TunerData> {
        return when {
            method.method == "select" -> {
                radio.select(method.params["index"]?.let { it as Number }?.toInt() ?: 0)
                        .andThen(Observable.defer { flowTuner(radio) })
                        .take(1)
            }
            else -> Observable.error(IllegalArgumentException("Cannot interpret $method"))
        }
                .firstOrError()
    }

    private fun showTunerMethods(fm: Radio): Observable<List<MethodDesc>> {
        return Observable.just(
                listOf(
                        MethodDesc(name = "select",
                                uri = "/tuners/fm/rpc",
                                verb = "POST",
                                example = Method(method = "select", params = mapOf("index" to 1))
                        )
                ))
    }
}

data class PlayerData(
        val isPlaying: Boolean,
        val nowPlayingIndex: Int,
        val nowPlaying: Track,
        val duration: Int,
        val rpc: String,
        val tracks: String
)

data class MethodDesc(val name: String, val uri: String, val verb: String, val example: Method)
data class Method(val method: String, val params: Map<String, Any>)

data class WsMessage<T>(val id: String, val payload: T)
