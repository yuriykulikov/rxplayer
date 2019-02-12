import com.google.gson.Gson;
import de.eso.rxplayer.EntertainmentImplKt;
import de.eso.rxplayer.vertx.RequestHandler;
import de.eso.rxplayer.vertx.VertxServer;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;
import kotlin.jvm.functions.Function1;

import java.util.Collections;

public class RxplayerMain {
    public static void main(String[] args) {
        System.out.println("hello world");
        Gson gs = new Gson();

        VertxServer serv = new VertxServer(7780, Collections.singletonList(new RequestHandler.GetHandler(
                (uri) -> true,
                (str, something) -> Observable.just("Hellow world")
        )), o -> gs.toJson(o), (aClass, s) -> gs.fromJson(s, aClass));

        serv.listen();
    }
}
