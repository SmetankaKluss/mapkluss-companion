package art.mapkluss.companion;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildSaveQueueTest {
    private final LiveBuildSourceCache.Reference source=new LiveBuildSourceCache.Reference(LiveBuildSessionStore.SourceKind.LITEMATIC,"a".repeat(64));
    private LiveBuildSessionStore.Snapshot snapshot(long time){
        return new LiveBuildSessionStore.Snapshot(source.kind(),source.sha256(),"b".repeat(64),"minecraft:overworld",1,1,0,time,List.of());
    }
    @Test void coalescesPendingSnapshotsAndRejectsOldSessionCallbacks(){
        var tasks=new ArrayDeque<Runnable>();var writes=new ArrayList<Long>();
        var queue=new LiveBuildSaveQueue(new LiveBuildSaveQueue.Writer(){
            public void save(LiveBuildSessionStore.Snapshot value){writes.add(value.savedAt());}
            public void clear(){writes.add(-1L);}
        },tasks::add);
        var token=queue.begin(source);
        var first=queue.save(token,snapshot(1));var second=queue.save(token,snapshot(2));var latest=queue.save(token,snapshot(3));
        assertFalse(first.join());assertFalse(second.join());assertFalse(latest.isDone());
        assertEquals(1,tasks.size());tasks.remove().run();assertTrue(latest.join());assertEquals(List.of(3L),writes);
        var next=queue.begin(source);assertFalse(queue.save(token,snapshot(4)).join());
        var saved=queue.save(next,snapshot(5));tasks.remove().run();assertTrue(saved.join());
        assertEquals(List.of(3L,5L),writes);assertTrue(queue.idle().isDone());
    }
    @Test void stopBarrierSurvivesNewBeginAndSaveFailure(){
        var tasks=new ArrayDeque<Runnable>();var writes=new ArrayList<Long>();
        var queue=new LiveBuildSaveQueue(new LiveBuildSaveQueue.Writer(){
            public void save(LiveBuildSessionStore.Snapshot value)throws IOException{writes.add(value.savedAt());throw new IOException("test");}
            public void clear(){writes.add(-1L);}
        },tasks::add);
        var token=queue.begin(source);var pending=queue.save(token,snapshot(1));var stop=queue.stop(token);
        assertFalse(pending.join());assertFalse(queue.save(token,snapshot(2)).join());
        var next=queue.begin(source);var failed=queue.save(next,snapshot(3));
        tasks.remove().run();assertTrue(stop.join());assertTrue(failed.isCompletedExceptionally());
        assertEquals(List.of(-1L,3L),writes);
        assertTrue(queue.idle().isDone());
    }
    @Test void inFlightWriteFinishesBeforeStopAndCannotResurrectSession(){
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->{
            var started=new CountDownLatch(1);var release=new CountDownLatch(1);
            var writes=Collections.synchronizedList(new ArrayList<Long>());
            ExecutorService worker=Executors.newSingleThreadExecutor();
            try{
                var queue=new LiveBuildSaveQueue(new LiveBuildSaveQueue.Writer(){
                    public void save(LiveBuildSessionStore.Snapshot value)throws IOException{
                        started.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IOException("test timeout");}
                        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
                        writes.add(value.savedAt());
                    }
                    public void clear(){writes.add(-1L);}
                },worker);
                var token=queue.begin(source);var first=queue.save(token,snapshot(1));
                assertTrue(started.await(2,TimeUnit.SECONDS));
                var pending=queue.save(token,snapshot(2));var stopped=queue.stop(token);
                assertFalse(pending.join());assertFalse(queue.save(token,snapshot(3)).join());
                release.countDown();assertTrue(first.get(2,TimeUnit.SECONDS));assertTrue(stopped.get(2,TimeUnit.SECONDS));
                queue.idle().get(2,TimeUnit.SECONDS);assertEquals(List.of(1L,-1L),writes);
            }finally{release.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
        });
    }
    @Test void supersededCompletionCallbackCannotLoseItsNewerWrite(){
        var tasks=new ArrayDeque<Runnable>();var writes=new ArrayList<Long>();
        var queue=new LiveBuildSaveQueue(new LiveBuildSaveQueue.Writer(){
            public void save(LiveBuildSessionStore.Snapshot value){writes.add(value.savedAt());}
            public void clear(){}
        },tasks::add);
        var token=queue.begin(source);var first=queue.save(token,snapshot(1));
        var fromCallback=first.thenCompose(done->queue.save(token,snapshot(3)));
        var second=queue.save(token,snapshot(2));
        tasks.remove().run();assertFalse(second.join());assertTrue(fromCallback.join());assertEquals(List.of(3L),writes);
    }
    @Test void executorRejectionCompletesFailureAndQueueCanRetry(){
        var tasks=new ArrayDeque<Runnable>();int[] calls={0};
        var queue=new LiveBuildSaveQueue(new LiveBuildSaveQueue.Writer(){
            public void save(LiveBuildSessionStore.Snapshot value){}
            public void clear(){}
        },task->{if(calls[0]++==0)throw new RejectedExecutionException();tasks.add(task);});
        var token=queue.begin(source);assertTrue(queue.save(token,snapshot(1)).isCompletedExceptionally());
        assertTrue(queue.idle().isDone());var second=queue.save(token,snapshot(2));tasks.remove().run();assertTrue(second.join());
    }
}
