package art.mapkluss.companion;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Client-thread lifecycle; IO and source parsing stay on workers. One instance per profile. */
public final class LiveBuildPersistence {
    public record Restored(LiveBuildSessionStore.Snapshot snapshot, LiveBuildSourceCache.Loaded source) { }
    private final LiveBuildSessionStore store;
    private final LiveBuildSourceCache cache;
    private final LiveBuildSaveQueue queue;
    private LiveBuildSaveQueue.Session session;
    private CompletableFuture<Restored> loading;
    private String attempted;
    private long nextSave;
    private volatile boolean failed;
    private boolean stopped;
    public LiveBuildPersistence(Path runDir) {
        store=LiveBuildSessionStore.forRunDir(runDir);cache=LiveBuildSourceCache.forRunDir(runDir);queue=new LiveBuildSaveQueue(store);
    }
    public LiveBuildSourceCache.Reference source(){return session==null?null:session.source();}
    public boolean failed(){return failed;}
    public void failureAt(long time){failed=true;nextSave=time+5000;}
    public boolean due(long time){return session!=null&&time>=nextSave;}
    public void activate(LiveBuildSourceCache.Reference reference){
        cancelLoad();session=queue.begin(reference);nextSave=0;failed=false;stopped=false;
    }
    public void save(LiveBuildSessionStore.Snapshot snapshot,long time){
        if(session==null||snapshot==null)return;
        nextSave=time+5000;
        queue.save(session,snapshot).whenComplete((ok,error)->{if(error!=null)failed=true;});
    }
    public void suspend(LiveBuildSessionStore.Snapshot snapshot){
        save(snapshot,0);session=null;cancelLoad();attempted=null;
    }
    public void stop(){
        if(session!=null)queue.stop(session).whenComplete((ok,error)->{if(error!=null)failed=true;});
        session=null;cancelLoad();stopped=true;attempted=null;
    }
    public Restored poll(String world,String dimension){
        if(session!=null)return null;
        String key=world+":"+dimension;
        if(stopped){
            if(attempted==null)attempted=key;
            if(key.equals(attempted))return null;
            stopped=false;
        }
        if(!key.equals(attempted)){
            cancelLoad();attempted=key;
            loading=queue.idle().thenApplyAsync(ignored->{
                try{
                    var snapshot=store.load();
                    if(snapshot==null||!snapshot.worldHash().equals(world)||!snapshot.dimension().equals(dimension))return null;
                    return new Restored(snapshot,cache.load(new LiveBuildSourceCache.Reference(snapshot.kind(),snapshot.sourceSha256())));
                }catch(Exception error){throw new java.util.concurrent.CompletionException(error);}
            });
        }
        if(loading==null||!loading.isDone())return null;
        var done=loading;loading=null;
        try{return done.join();}catch(RuntimeException error){failed=true;return null;}
    }
    private void cancelLoad(){
        if(loading!=null){loading.whenComplete((old,error)->{if(old!=null)old.source().close();});loading=null;}
    }
    /** Controlled process shutdown only; not called by the tick loop. */
    public void finishWrites(){
        try{queue.idle().get(4,TimeUnit.SECONDS);}catch(Exception error){failed=true;}
    }
}
