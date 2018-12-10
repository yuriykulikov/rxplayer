import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import de.eso.rxplayer.EntertainmentService
import de.eso.rxplayer.Track
import de.eso.rxplayer.vertx.*
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
    lateinit var client: HttpClient
    lateinit var adapter: ApiAdapter
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
        VertxServer(
                port = port,
                adapter = adapter
        ).listen()
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
                .contains("{\"resources\": [\"/players\", \"/tuners\"] }")
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
}