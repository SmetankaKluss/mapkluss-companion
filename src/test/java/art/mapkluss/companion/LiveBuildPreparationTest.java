package art.mapkluss.companion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildPreparationTest {
    private final LiveBuildProgress.State stone = new LiveBuildProgress.State("minecraft:stone", Map.of());
    private final LiveBuildProgress.State dirt = new LiveBuildProgress.State("minecraft:dirt", Map.of());
    private LiveBuildPreparation job() {
        var cells = List.of(
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),stone),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(1,0,0),stone),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(2,0,0),dirt));
        var source = new LiveBuildSchematic("a".repeat(64),cells);
        return new LiveBuildPreparation(source,new LiveBuildProgress.Identity(source.sha256(),"test","minecraft:overworld",
            new LiveBuildProgress.Position(0,0,0),0));
    }
    @Test void resolvesUniqueStatesOnCallerThreadWithStrictBudget() {
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->{
            var thread = Thread.currentThread();
            var calls = new AtomicInteger();
            try(var job = job()) {
                LiveBuildAssembly result = null;
                while(result == null) {
                    int before = calls.get();
                    result = job.advance(state -> {
                        assertSame(thread,Thread.currentThread()); calls.incrementAndGet(); return state;
                    },1);
                    assertTrue(calls.get()-before<=1);
                    Thread.sleep(1);
                }
                assertEquals(2,calls.get());
                assertEquals(3,result.size());
                assertEquals(stone,result.cell(1).expected());
            }
        });
    }
    @Test void cancellationPreventsLaterPublicationAndRegistryAccess() {
        var job = job();
        job.close();
        assertThrows(CancellationException.class,()->job.advance(state->{fail("Cancelled registry access");return state;},1));
    }
}
