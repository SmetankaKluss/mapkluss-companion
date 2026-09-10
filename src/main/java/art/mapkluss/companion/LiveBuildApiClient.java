package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Blocking worker-only client. No world access, background polling or implicit mutations. */
public final class LiveBuildApiClient implements LiveBuildGroupController.Api {
    private static final Gson GSON = new Gson();
    private static final String PATH = "/functions/v1/companion-build";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final URI endpoint;
    private final CompanionConfig config;
    private final String anonKey;
    private final String token;

    public enum Action {
        WEB_BEGIN("web_begin", false, 8192), WEB_PUBLISH("web_publish", false, 4600000), WEB_STOP("web_stop", false, 8192),
        CREATE("create", false, 8192), JOIN("join", false, 8192), READ("read", true, 8192),
        INVITE("invite", false, 8192), LEAVE("leave", false, 8192), CLOSE("close", false, 8192),
        REMOVE_MEMBER("remove_member", false, 8192), PLACE("place", false, 8192), UNPLACE("unplace", false, 8192),
        BEGIN("observe_begin", false, 8192), UPLOAD("observe_batch", false, 393216),
        DOWNLOAD("observations_batch", true, 32768), SOURCE_BEGIN("source_begin",false,8192), SOURCE_READ("source_read",true,8192);
        final String wire;
        final boolean readOnly;
        final int bytes;
        Action(String wire, boolean readOnly, int bytes) { this.wire = wire; this.readOnly = readOnly; this.bytes = bytes; }
    }

    public LiveBuildApiClient(CompanionConfig config, String token) {
        this(URI.create(config.supabaseUrl().replaceAll("/+$", "") + PATH), config.supabaseAnonKey(), token, config);
    }

    LiveBuildApiClient(URI endpoint, String anonKey, String token) { this(endpoint, anonKey, token, null); }

    private LiveBuildApiClient(URI endpoint, String anonKey, String token, CompanionConfig config) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.anonKey = Objects.requireNonNull(anonKey);
        if (token == null || token.isBlank() || token.length() > 8192) throw new IllegalArgumentException("Login required");
        this.token = token;
        this.config = config;
    }

    public JsonObject call(Action action, JsonObject fields) throws IOException, InterruptedException {
        Objects.requireNonNull(action);
        JsonObject body = Objects.requireNonNull(fields).deepCopy();
        if (body.has("action")) throw new IllegalArgumentException("Duplicate action");
        body.addProperty("action", action.wire);
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > action.bytes) throw new IOException("Build request is too large");
        URI target = config == null ? endpoint : URI.create(CompanionBackendRouter.select(config) + PATH);
        URI direct = config == null ? endpoint : CompanionBackendRouter.directEndpoint(config, PATH);
        HttpResponse<byte[]> response;
        boolean attemptedDirect = target.equals(direct);
        try { response = send(target, bytes); }
        catch (IOException error) {
            if (config == null || target.equals(direct)) throw error;
            CompanionBackendRouter.reportFailure(config, target);
            if (!action.readOnly) throw error;
            attemptedDirect = true;
            response = send(direct, bytes);
        }
        if (config != null && !attemptedDirect && response.statusCode() >= 502 && response.statusCode() <= 504) {
            CompanionBackendRouter.reportFailure(config, target);
            if (action.readOnly) response = send(direct, bytes);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ApiException(response.statusCode());
        try {
            JsonObject json = GSON.fromJson(new String(response.body(), StandardCharsets.UTF_8), JsonObject.class);
            if (json == null || !json.has("api_version") || !"1".equals(json.get("api_version").getAsString())
                || !json.has("build") || !json.get("build").isJsonObject()) throw new IllegalArgumentException();
            return json;
        } catch (RuntimeException error) { throw new IOException("Invalid build API response"); }
    }

    private HttpResponse<byte[]> send(URI target, byte[] bytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(12))
            .header("apikey", anonKey).header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var future = HTTP.sendAsync(request, info -> new BoundedBody(info.statusCode() >= 200 && info.statusCode() < 300 ? 524288 : 65536));
        try { return future.get(12, TimeUnit.SECONDS); }
        catch (TimeoutException error) { future.cancel(true); throw new IOException("Build request timed out"); }
        catch (InterruptedException error) { future.cancel(true); throw error; }
        catch (ExecutionException error) { throw new IOException("Build request failed"); }
    }
    @Override public byte[] source(String mode,String build,int part,byte[] bytes)throws IOException,InterruptedException{
        if(!List.of("put","get","finalize").contains(mode)||!java.util.UUID.fromString(build).toString().equals(build)
            ||part<0||part>63||bytes==null||bytes.length>2097152)throw new IllegalArgumentException("Invalid source request");
        URI target=config==null?endpoint:URI.create(CompanionBackendRouter.select(config)+PATH);
        URI direct=config==null?endpoint:CompanionBackendRouter.directEndpoint(config,PATH);
        HttpResponse<byte[]> response;
        boolean attemptedDirect=target.equals(direct);
        try{response=sendSource(target,mode,build,part,bytes);}
        catch(IOException failure){
            if(config==null||target.equals(direct)||!mode.equals("get"))throw failure;
            CompanionBackendRouter.reportFailure(config,target);attemptedDirect=true;response=sendSource(direct,mode,build,part,bytes);
        }
        if(!attemptedDirect&&mode.equals("get")&&response.statusCode()>=502&&response.statusCode()<=504)
            response=sendSource(direct,mode,build,part,bytes);
        if(response.statusCode()<200||response.statusCode()>=300)throw new ApiException(response.statusCode());
        return response.body();
    }
    private HttpResponse<byte[]> sendSource(URI target,String mode,String build,int part,byte[] bytes)throws IOException,InterruptedException{
        int seconds=mode.equals("finalize")?120:30;
        var request=HttpRequest.newBuilder(URI.create(target.toString()+"?source="+mode)).timeout(Duration.ofSeconds(seconds))
            .header("apikey",anonKey).header("Authorization","Bearer "+token).header("Content-Type","application/octet-stream")
            .header("x-build-id",build).header("x-build-part",String.valueOf(part))
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var pending=HTTP.sendAsync(request,info->new BoundedBody(info.statusCode()>=200&&info.statusCode()<300&&mode.equals("get")?2097152:65536));
        try{return pending.get(seconds,TimeUnit.SECONDS);}
        catch(TimeoutException failure){pending.cancel(true);throw new IOException("Source transfer timed out");}
        catch(InterruptedException failure){pending.cancel(true);throw failure;}
        catch(ExecutionException failure){throw new IOException("Source transfer failed");}
    }

    public static final class ApiException extends IOException {
        private final int status;
        ApiException(int status) { super("Build API HTTP " + status); this.status = status; }
        public int status() { return status; }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;
        private Flow.Subscription subscription;
        private int size;
        private boolean failed;
        BoundedBody(int limit) { this.limit = limit; }
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        public void onNext(List<ByteBuffer> items) {
            if (failed) return;
            for (ByteBuffer item : items) {
                if (item.remaining() > limit - size) {
                    failed = true; subscription.cancel(); delegate.onError(new IOException("Build response is too large")); return;
                }
                size += item.remaining();
            }
            delegate.onNext(items);
        }
        public void onError(Throwable error) { if (!failed) delegate.onError(error); }
        public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
