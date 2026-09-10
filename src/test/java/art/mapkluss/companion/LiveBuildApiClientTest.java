package art.mapkluss.companion;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildApiClientTest {
    @Test void binarySourceUsesAuthenticatedPartsAndDoesNotRetryWrites()throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        var auth=new AtomicReference<String>();var part=new AtomicReference<String>();var query=new AtomicReference<String>();
        server.createContext("/",e->{
            calls.incrementAndGet();auth.set(e.getRequestHeaders().getFirst("Authorization"));part.set(e.getRequestHeaders().getFirst("x-build-part"));query.set(e.getRequestURI().getQuery());
            e.getRequestBody().readAllBytes();byte[] bytes={1,2,3};
            e.sendResponseHeaders(query.get().equals("source=get")?200:503,bytes.length);e.getResponseBody().write(bytes);e.close();
        });server.start();
        try{
            var client=client(server);String id="11111111-1111-4111-8111-111111111111";
            assertArrayEquals(new byte[]{1,2,3},client.source("get",id,2,new byte[0]));
            assertEquals("Bearer fixture-token",auth.get());assertEquals("2",part.get());assertEquals("source=get",query.get());
            assertThrows(LiveBuildApiClient.ApiException.class,()->client.source("put",id,2,new byte[]{1}));assertEquals(2,calls.get());
            assertThrows(IllegalArgumentException.class,()->client.source("put",id,2,new byte[2097153]));assertEquals(2,calls.get());
        }finally{server.stop(0);}
    }
    @Test void realHttpUsesAuthAndExactWireActionWithoutMutatingInput() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var captured=new AtomicReference<String>();var auth=new AtomicReference<String>();
        server.createContext("/",e->{
            captured.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            auth.set(e.getRequestHeaders().getFirst("Authorization"));
            byte[] reply="{\"api_version\":1,\"build\":{\"accepted\":true}}".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200,reply.length);e.getResponseBody().write(reply);e.close();
        });server.start();
        try {
            var client=client(server);var input=new JsonObject();input.addProperty("consent",true);
            assertTrue(client.call(LiveBuildApiClient.Action.CREATE,input).getAsJsonObject("build").get("accepted").getAsBoolean());
            assertEquals("Bearer fixture-token",auth.get());assertTrue(captured.get().contains("\"action\":\"create\""));
            assertFalse(input.has("action"));
        } finally {server.stop(0);}
    }
    @Test void mutationsDoNotRetryAndErrorsDoNotExposeResponseBody() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        server.createContext("/",e->{calls.incrementAndGet();byte[] reply="private request data".getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(503,reply.length);e.getResponseBody().write(reply);e.close();});server.start();
        try {
            var error=assertThrows(LiveBuildApiClient.ApiException.class,()->client(server).call(LiveBuildApiClient.Action.UPLOAD,new JsonObject()));
            assertEquals(503,error.status());assertFalse(error.getMessage().contains("private"));assertEquals(1,calls.get());
        } finally {server.stop(0);}
    }
    @Test void redirectsNeverForwardCredentials() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var leaked=new AtomicInteger();
        server.createContext("/",e->{e.getResponseHeaders().set("Location","/other");e.sendResponseHeaders(307,-1);e.close();});
        server.createContext("/other",e->{leaked.incrementAndGet();e.sendResponseHeaders(200,-1);e.close();});server.start();
        try {assertThrows(IOException.class,()->client(server).call(LiveBuildApiClient.Action.READ,new JsonObject()));assertEquals(0,leaked.get());}
        finally {server.stop(0);}
    }
    @Test void rejectsOversizedResponseAndRequest() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        server.createContext("/",e->{calls.incrementAndGet();byte[] data=new byte[524289];
            e.sendResponseHeaders(200,data.length);try{e.getResponseBody().write(data);}finally{e.close();}});server.start();
        try {
            var client=client(server);
            assertThrows(IOException.class,()->client.call(LiveBuildApiClient.Action.DOWNLOAD,new JsonObject()));
            var input=new JsonObject();input.addProperty("blob","x".repeat(8192));
            assertThrows(IOException.class,()->client.call(LiveBuildApiClient.Action.CREATE,input));assertEquals(1,calls.get());
        } finally {server.stop(0);}
    }
    private static LiveBuildApiClient client(HttpServer server) {
        return new LiveBuildApiClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),"fixture-key","fixture-token");
    }
    @Test void gatewayFallsBackOnlyForReadActions() throws Exception {
        var gateway=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var direct=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var gatewayCalls=new AtomicInteger();var directCalls=new AtomicInteger();
        gateway.createContext("/readyz",e->{e.sendResponseHeaders(200,-1);e.close();});
        gateway.createContext("/functions/",e->{gatewayCalls.incrementAndGet();e.sendResponseHeaders(503,-1);e.close();});
        direct.createContext("/functions/",e->{directCalls.incrementAndGet();e.sendResponseHeaders(503,-1);e.close();});
        gateway.start();direct.start();
        try {
            var config=new CompanionConfig("http://127.0.0.1:"+direct.getAddress().getPort(),"fixture-key","https://example.test","en",
                "http://127.0.0.1:"+gateway.getAddress().getPort());
            var client=new LiveBuildApiClient(config,"fixture-token");
            CompanionBackendRouter.clearCacheForTests();
            assertThrows(IOException.class,()->client.call(LiveBuildApiClient.Action.UPLOAD,new JsonObject()));
            assertEquals(1,gatewayCalls.get());assertEquals(0,directCalls.get());
            CompanionBackendRouter.clearCacheForTests();
            assertThrows(IOException.class,()->client.call(LiveBuildApiClient.Action.DOWNLOAD,new JsonObject()));
            assertEquals(2,gatewayCalls.get());assertEquals(1,directCalls.get());
        } finally {gateway.stop(0);direct.stop(0);CompanionBackendRouter.clearCacheForTests();}
    }
}
