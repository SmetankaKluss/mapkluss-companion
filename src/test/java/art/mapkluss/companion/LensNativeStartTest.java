package art.mapkluss.companion;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LensNativeStartTest {
    @Test void nativeStartSeedsSameSessionAndRetainsInviteWithoutRetry() throws Exception {
        var requests=new ArrayList<JsonObject>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/functions/v1/companion-lens",exchange->{
            var request=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            requests.add(request);
            String response=requests.size()==1
                ?"{\"session\":{\"sessionId\":\"s\",\"grid\":{\"wide\":1,\"tall\":1},\"revision\":4,\"sessionCode\":\"ABC123\"},\"publisherLease\":\"private-fixture\"}"
                :"{\"session\":{\"sessionId\":\"s\",\"grid\":{\"wide\":1,\"tall\":1},\"revision\":5}}";
            response = response.replaceFirst("\\{", "{\"apiVersion\":1,");
            var bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try{
            var api=new LensApiClient("http://127.0.0.1:"+server.getAddress().getPort(),"fixture","fixture");
            var m=new CompanionManifest("a","v","o","Test","private",new CompanionManifest.Grid(1,1),"2d","1.21.11","standard",null,false,List.of(),List.of(),null);
            var result=api.start(m,new byte[]{1,2,3});
            assertEquals(2,requests.size());assertEquals("ABC123",result.sessionCode());assertEquals(5,result.revision());
            assertEquals("session_seed",requests.get(1).get("action").getAsString());
            assertEquals(4,requests.get(1).get("baseRevision").getAsInt());
            assertEquals("device",requests.get(1).get("principalKind").getAsString());
        }finally{server.stop(0);}
    }
}
