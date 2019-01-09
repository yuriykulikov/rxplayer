import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.eso.rxplayer.Album
import de.eso.rxplayer.Artist
import de.eso.rxplayer.EntertainmentService
import de.eso.rxplayer.Track
import de.eso.rxplayer.vertx.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.ServiceHelper
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpMethod
import io.vertx.core.spi.VertxFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket


class VertxTest {
    val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    lateinit var client: HttpClient
    lateinit var adapter: ApiAdapter
    lateinit var vertxServer: VertxServer

    var port: Int = 0
    @Before
    fun setup() {
        val vertx = ServiceHelper.loadFactory(VertxFactory::class.java)
                .vertx()
        val socket = ServerSocket(0)
        port = socket.localPort
        socket.close()
        client = vertx.createHttpClient()

        adapter = ApiAdapter(
                entertainment = EntertainmentService(scheduler = Schedulers.single()),
                moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        )
        vertxServer = VertxServer(
                port = port,
                adapter = adapter
        )
        vertxServer.listen()
    }

    fun requestRaw(method: HttpMethod, uri: String, vararg body: String): Single<Buffer> {
        return Single.create { emitter ->
            val request = client //
                    .request(method, port, "localhost", uri)
                    .handler { resp -> resp.bodyHandler { emitter.onSuccess(it) } }

            if (body.size == 1) {
                request.putHeader("Content-Length", "" + body[0].length)
                request.write(body[0])
            }

            request.end()
        }
    }

    @Test
    fun `Root lists resources`() {
        requestRaw(HttpMethod.GET, "")
                .map { it.toString() }
                .blockingGet()
                .let { assertThat(it) }
                .contains("resources", "players", "tuners", "artists", "albums")
    }

    @Test
    fun `Invalid links are not handled`() {
        requestRaw(HttpMethod.GET, "/bs")
                .map { it.toString() }
                .blockingGet()
                .let { assertThat(it) }
                .contains("bs cannot be handled")
    }

    @Test
    fun `Usb player should not be playing`() {
        val player: PlayerData = requestRaw(HttpMethod.GET, "/players/usb")
                .map { it.toString() }
                .doOnSuccess { println(it) }
                .map { adapter.playerMoshi.fromJson(it)!! }
                .blockingGet()

        println(player)

        assertThat(player.isPlaying).isFalse()
        assertThat(player.duration).isEqualTo(222)
        assertThat(player.nowPlayingIndex).isEqualTo(0)
        assertThat(player.nowPlaying.title).isEqualTo("Trap Queen")
    }

    @Test
    fun `Sd player should not be playing`() {
        val player: PlayerData = requestRaw(HttpMethod.GET, "/players/sd")
                .map { it.toString() }
                .doOnSuccess { println(it) }
                .map { adapter.playerMoshi.fromJson(it)!! }
                .blockingGet()

        println(player)

        assertThat(player.isPlaying).isFalse()
        assertThat(player.duration).isEqualTo(177)
        assertThat(player.nowPlayingIndex).isEqualTo(0)
        assertThat(player.nowPlaying.title).isEqualTo("HUMBLE.")
    }

    @Test
    fun `Sd player tracks should be showing something`() {
        val list: List<Track> = requestRaw(HttpMethod.GET, "/players/sd/tracks")
                .map { it.toString() }
                .map { adapter.tracksMoshi.fromJson(it)!! }
                .blockingGet()

        println(list)

        assertThat(list).hasSize(50)
    }

    @Test
    fun `Paging should work`() {
        val list: List<Track> = requestRaw(HttpMethod.GET, "/players/sd/tracks?from=10&to=20")
                .map { it.toString() }
                .map { adapter.tracksMoshi.fromJson(it)!! }
                .blockingGet()

        println(list)

        assertThat(list).hasSize(10)
    }


    @Test
    fun `RPC endpoint gives a readable description for the RPC`() {
        val list: List<MethodDesc> = requestRaw(HttpMethod.GET, "/players/sd/rpc")
                .map { it.toString() }
                .doOnSuccess { println(it) }
                .map { adapter.methodsDescMoshi.fromJson(it)!! }
                .blockingGet()

        println(list)

        assertThat(list.first().example.params["index"] as Double).isEqualTo(1.0)
    }

    @Test
    fun `RPC select changes the track`() {
        val method = Method(method = "select", params = mapOf("index" to 1))

        val body = adapter.methodMoshi.toJson(method)

        requestRaw(HttpMethod.POST, "/players/sd/rpc", body)
                .map { it.toString() }
                .doOnSuccess { println("response: $it") }
                .map { adapter.playerMoshi.fromJson(it)!! }
                .blockingGet()

        val player: PlayerData = requestRaw(HttpMethod.GET, "/players/sd")
                .map { it.toString() }
                .doOnSuccess { println("state: $it") }
                .map { adapter.playerMoshi.fromJson(it)!! }
                .blockingGet()

        assertThat(player.nowPlayingIndex).isEqualTo(1)
    }

    @Test
    fun `Play pause works`() {
        val method = Method(method = "play", params = mapOf())

        val body = adapter.methodMoshi.toJson(method)

        requestRaw(HttpMethod.POST, "/players/sd/rpc", body)
                .map { it.toString() }
                .doOnSuccess { println("response: $it") }
                .map { adapter.playerMoshi.fromJson(it)!! }
                .blockingGet()

        val player: PlayerData = requestRaw(HttpMethod.GET, "/players/sd")
                .map { it.toString() }
                .doOnSuccess { println("state: $it") }
                .map { adapter.playerMoshi.fromJson(it)!! }
                .blockingGet()

        assertThat(player.isPlaying).isTrue()
    }

    @Test
    fun `Players lists them`() {
        val player: PlayerData = requestRaw(HttpMethod.GET, "/players")
                .map { it.toString() }
                .doOnSuccess { println(it) }
                .map { adapter.playersMoshi.fromJson(it)!! }
                .blockingGet()
                .first()

        println(player)

        assertThat(player.isPlaying).isFalse()
        assertThat(player.duration).isEqualTo(222)
        assertThat(player.nowPlayingIndex).isEqualTo(0)
        assertThat(player.nowPlaying.title).isEqualTo("Trap Queen")
    }


    @Test
    fun `Tuners lists them`() {
        val player: ApiAdapter.TunerData = requestRaw(HttpMethod.GET, "/tuners")
                .map { it.toString() }
                .doOnSuccess { println(it) }
                .map { adapter.tunersMoshi.fromJson(it)!! }
                .blockingGet()
                .first()

        println(player)

        assertThat(player.stationIndex).isEqualTo(0)
        assertThat(player.radioText.title).isNotEmpty()
    }


    @Test
    fun `Tuners rpc changes the station and the track`() {
        val method = Method(method = "select", params = mapOf("index" to 1))

        val body = adapter.methodMoshi.toJson(method)

        requestRaw(HttpMethod.POST, "/tuners/fm/rpc", body)
                .map { it.toString() }
                .doOnSuccess { println("response: $it") }
                .map { adapter.tunerMoshi.fromJson(it)!! }
                .blockingGet()

        val player: ApiAdapter.TunerData = requestRaw(HttpMethod.GET, "/tuners/fm")
                .map { it.toString() }
                .doOnSuccess { println("state: $it") }
                .map { adapter.tunerMoshi.fromJson(it)!! }
                .blockingGet()

        assertThat(player.stationIndex).isEqualTo(1)
        assertThat(player.station.name).isEqualTo("88.9 wsnd FW")
        assertThat(player.radioText.title).isNotEmpty()
    }

    @Test
    fun `artists are available at artists`() {
        val artists: List<Artist> = requestRaw(HttpMethod.GET, "/artists")
                .map { it.toString() }
                .map { adapter.artistsMoshi.fromJson(it)!! }
                .blockingGet()

        println(artists)

        assertThat(artists).hasSize(6)
    }

    @Test
    fun `Single artist can be queried`() {
        val artist: Artist = requestRaw(HttpMethod.GET, "/artists/1")
                .doOnSuccess { println(it) }
                .map { it.toString() }
                .map { adapter.adapter(Artist::class.java).fromJson(it)!! }
                .blockingGet()

        println(artist)

        assertThat(artist.id).isEqualTo(1)
    }

    @Test
    fun `Albums are available at albums`() {
        val artists: List<Album> = requestRaw(HttpMethod.GET, "/albums")
                .map { it.toString() }
                .map { adapter.albumsMoshi.fromJson(it)!! }
                .blockingGet()

        assertThat(artists).hasSize(195)
    }

    // TODO: test multiple values & obs$ not closed and no onError
    // TODO: use TestScheduler instead of Schedulers#single() -> advanceTime
    @Test
    fun `Tuners resource returns current TunerData on subscription`() {
        val tuners = ws("/tuners", "100500", Array<ApiAdapter.TunerData>::class.java)
                .map { it.payload }
                .map { it[0] }
                .firstOrError()
                .blockingGet()

        assertThat(tuners.radioText.title).isNotEmpty()
    }


    @Test
    fun `Single album can be queried with WS`() {
        val album = ws("/albums/1", "100500", Album::class.java)
                .firstOrError()
                .blockingGet()
                .payload

        assertThat(album.name).isEqualTo("Fetty Wap")
    }

    fun WsRequest.toJson(): String {
        return vertxServer.wsRequestMoshi.toJson(this)
    }

    fun <T> ws(uri: String, id: String, type: Class<T>): Observable<WsMessage<T>> {
        val moshiAdapter = moshi.adapter<WsMessage<T>>(Types.newParameterizedType(WsMessage::class.java, type))
        return Observable.create<Buffer> { emitter ->
            client.websocket(
                    port,
                    "localhost",
                    "/ws"
            ) { wsConnect ->
                wsConnect.exceptionHandler {
                    emitter.onError(it)
                }
                wsConnect.handler { wsBuffer -> emitter.onNext(wsBuffer) }
                // create subscription
                wsConnect.writeTextMessage(WsRequest(uri, id, true).toJson())
            }
        }
                .map { wsBuffer -> wsBuffer.getString(0, wsBuffer.length()) }
                .map { bodyString -> moshiAdapter.fromJson(bodyString) }
    }

    @Test
    fun `Websocket should stream`() {
        val playerData = ws("/players/sd", "1", PlayerData::class.java)
                .firstOrError()
                .blockingGet()
        assertThat(playerData.id).isEqualTo("1")
        assertThat(playerData.payload.duration).isEqualTo(177)
    }
}