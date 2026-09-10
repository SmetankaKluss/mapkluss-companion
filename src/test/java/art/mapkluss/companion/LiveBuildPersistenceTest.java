package art.mapkluss.companion;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildPersistenceTest {
    @TempDir Path run;
    private final String world="a".repeat(64),dimension="minecraft:overworld";
    @Test void saveSuspendAndNewProcessReopenMatchingWorldOnly(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var cache=LiveBuildSourceCache.forRunDir(run);
            try(var source=cache.importBytes(SuppressionTestFixtures.litematicV3Bytes(),LiveBuildSessionStore.SourceKind.LITEMATIC)){
                var snapshot=new LiveBuildSessionStore.Snapshot(source.reference().kind(),source.reference().sha256(),world,dimension,1,1,0,123,List.of());
                var first=new LiveBuildPersistence(run);first.activate(source.reference());
                first.suspend(snapshot);first.finishWrites();assertFalse(first.failed());
                var restarted=new LiveBuildPersistence(run);
                for(int i=0;i<20;i++){assertNull(restarted.poll("b".repeat(64),dimension));Thread.sleep(1);}
                assertNotNull(LiveBuildSessionStore.forRunDir(run).load());
                LiveBuildPersistence.Restored loaded=null;
                while(loaded==null){loaded=restarted.poll(world,dimension);Thread.sleep(1);}
                try(var restored=loaded.source()){
                    assertEquals(snapshot,loaded.snapshot());assertEquals(source.reference(),restored.reference());
                    restarted.activate(restored.reference());restarted.stop();restarted.finishWrites();
                    assertNull(LiveBuildSessionStore.forRunDir(run).load());
                    assertNull(restarted.poll(world,dimension));
                }
            }
        });
    }
}
