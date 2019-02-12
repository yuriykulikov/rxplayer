import com.google.gson.Gson;
import de.eso.rxplayer.EntertainmentService;
import de.eso.rxplayer.vertx.RequestHandler;
import de.eso.rxplayer.vertx.VertxServer;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;

public class RxplayerMain {
    public static void main(String[] args) {
        ArrayList<RequestHandler.GetHandler> handlers = new ArrayList<>();

        EntertainmentService entertainmentService = new EntertainmentService(Schedulers.single(), true);

        handlers.add(new RequestHandler.GetHandler(
                uri -> uri.contains("albums"),
                // /albums?id=1
                (uri, params) -> {
                    int id = Integer.parseInt(params.get("id"));
                    return entertainmentService.getBrowser().albumById(id).toObservable();
                }
        ));

        handlers.add(new RequestHandler.GetHandler(
                uri -> uri.contains("stations"),
                (uri, params) -> entertainmentService.getFm().list()
        ));

        handlers.add(new RequestHandler.GetHandler(
                uri -> uri.contains("tuner"),
                (uri, params) -> entertainmentService.getFm().radioText()
        ));

        Gson gson = new Gson();
        VertxServer server = new VertxServer(7780,
                handlers,
                object -> gson.toJson(object),
                (clazz, string) -> gson.fromJson(string, clazz));

        server.listen();
    }
}
