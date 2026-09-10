package art.mapkluss.companion;

import java.io.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Session-owned, memory-only preview. Gathering is not evidence of placed blocks. */
public final class GatheringArtPreview implements AutoCloseable {
    @FunctionalInterface public interface SourceResolver { String resolve(BuildSessionState session) throws Exception; }
    private final SourceResolver resolver;
    public GatheringArtPreview(){this(BuildSessionState::image_preview);}
    public GatheringArtPreview(SourceResolver resolver){this.resolver=java.util.Objects.requireNonNull(resolver);}
    public synchronized void retry(){epoch++;busy=false;failed=false;rendered=null;}
    public record Frame(int width,int height,int[] pixels,long revision) { }
    private static final ExecutorService WORKER=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),
        r->{var t=new Thread(r,"mapkluss-gather-preview");t.setDaemon(true);return t;});
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Frame frame;
    private volatile boolean failed;
    private long epoch;
    private long revision;
    private String source;
    private BuildSessionState rendered;
    private java.util.Map<String,Integer> renderedColours=java.util.Map.of();
    private GatheringPixelReveal reveal;
    private int[] original;
    private int width,height;
    private boolean busy,closed;
    public Frame frame(){return frame;}
    public boolean failed(){return failed;}
    public synchronized void update(BuildSessionState session, java.util.Map<String,Integer> colours){
        if(closed||session==null)return;
        String next=session.image_preview();
        if(!java.util.Objects.equals(source,next)){
            epoch++;source=next;original=null;frame=null;failed=false;busy=false;rendered=null;reveal=null;
        }
        if(busy||failed||(session.equals(rendered)&&colours.equals(renderedColours)))return;
        busy=true;long token=epoch;String url=source;
        var palette=java.util.Map.copyOf(colours);
        var previousReveal=palette.equals(renderedColours)?reveal:null;
        var existing=original;int w=width,h=height;
        try{CompletableFuture.runAsync(()->{
            try{
                synchronized(this){if(closed||epoch!=token)return;}
                Frame decoded=existing==null?decode(resolver.resolve(session)):new Frame(w,h,existing,0);
                var nextReveal=previousReveal==null?new GatheringPixelReveal(decoded.pixels(),palette):previousReveal;
                int[] output=nextReveal.render(session,palette);
                synchronized(this){if(closed||epoch!=token)return;original=decoded.pixels();width=decoded.width();height=decoded.height();
                    frame=new Frame(width,height,output,++revision);rendered=session;renderedColours=palette;reveal=nextReveal;busy=false;}
            }catch(Exception invalid){synchronized(this){if(epoch==token){busy=false;failed=true;}}}
        },WORKER);}catch(RejectedExecutionException saturated){busy=false;}
    }
    static int percent(BuildSessionState session){
        long total=0,done=0;
        if(session.materials()!=null)for(var material:session.materials()){
            int count=Math.max(0,material.count());total+=count;
            done+=Math.max(0,Math.min(count,session.gathered()==null?0:session.gathered().getOrDefault(material.nbtName(),0)));
        }
        return total==0?0:(int)(done*100/total);
    }
    private static Frame decode(String source)throws Exception {
        if(source==null||source.isBlank())throw new IOException("Preview missing");
        byte[] bytes;
        if(source.startsWith("data:image/png;base64,")||source.startsWith("data:image/jpeg;base64,")){
            if(source.length()>23_000_000)throw new IOException("Preview too large");
            bytes=Base64.getDecoder().decode(source.substring(source.indexOf(',')+1));
        }else{
            var request=HttpRequest.newBuilder(CompanionApiClient.requireTrustedDownloadUri(source)).timeout(Duration.ofSeconds(20)).GET().build();
            var response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var body=response.body()){
                if(response.statusCode()!=200)throw new IOException("Preview unavailable");
                bytes=CompanionApiClient.readBounded(body,16*1024*1024);
            }
        }
        try(var input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Invalid preview");
            var reader=readers.next();try{
                reader.setInput(input,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>4096||h>4096)throw new IOException("Preview dimensions too large");
                var parameters=reader.getDefaultReadParam();int sample=Math.max(1,(Math.max(w,h)+1279)/1280);
                parameters.setSourceSubsampling(sample,sample,0,0);var image=reader.read(0,parameters);
                return new Frame(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()),0);
            }finally{reader.dispose();}
        }
    }
    @Override public synchronized void close(){closed=true;epoch++;original=null;frame=null;reveal=null;rendered=null;}
}
