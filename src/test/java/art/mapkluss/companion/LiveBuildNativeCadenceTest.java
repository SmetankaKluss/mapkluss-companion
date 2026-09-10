package art.mapkluss.companion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildNativeCadenceTest {
    private static byte[] denseCatalog() throws Exception {
        byte[] schematic=SuppressionTestFixtures.litematicV3Bytes();
        var nbt=SuppressionNbt.readCompressed(schematic);
        var root=SuppressionNbt.compound(nbt.root(),"root");
        var regions=SuppressionNbt.compound(root.get("Regions"),"regions");
        var region=SuppressionNbt.compound(regions.get("fixture"),"region");
        var packed=(long[])region.get("BlockStates").value();
        for(int z=0;z<128;z++)for(int x=0;x<128;x++){
            int bit=(129*128+(z+1)*128+x)*2;packed[bit/64]|=1L<<(bit%64);
        }
        schematic=SuppressionNbt.writeCompressed(nbt);
        byte[] plan=SuppressionTestFixtures.planV3Bytes(schematic);
        var bundle=new SuppressionBundle(SuppressionPlanParser.parse(plan),plan,schematic,
            SuppressionHashes.sha256(plan),SuppressionHashes.sha256(schematic),null,null,"fixture","fixture");
        var tiles=new ArrayList<SuppressionBundleCatalog.Tile>();
        for(int i=0;i<100;i++)tiles.add(new SuppressionBundleCatalog.Tile(String.format(Locale.ROOT,"tile_%03d",i+1),i+1,i%10,i/10,bundle));
        return LiveBuildCatalogSource.encode(new SuppressionBundleCatalog(null,null,"fixture","fixture",null,10,10,tiles));
    }
    private static void ready(LiveBuildBundleRuntime runtime)throws Exception{
        while(runtime.preparing()){
            for(int i=0;i<64&&runtime.preparing();i++)runtime.prepare(s->s,(s,t)->s);
            Thread.sleep(1);
        }
        assertFalse(runtime.failed());
    }
    @Test void hundredDenseMapsStayFreshAtNativeReadCadenceWithoutStarvingSelection(){
        assertTimeoutPreemptively(Duration.ofSeconds(90),()->{
            var workspace=LiveBuildBundleWorkspace.read(denseCatalog());
            var stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
            try(var runtime=new LiveBuildBundleRuntime(workspace,"a".repeat(64),"minecraft:overworld",i->0xff808080)){
                ready(runtime);
                for(int i=0;i<100;i++){
                    runtime.select(i);ready(runtime);runtime.anchor(new LiveBuildProgress.Position(i*1000,0,0),LiveBuildTransform.NONE);ready(runtime);
                }
                long readyAt=0,firstComplete=-1;
                for(int tick=0;tick<=8000;tick++){
                    long now=tick*50L;
                    if(runtime.preparing()&&now>=readyAt)ready(runtime);
                    for(int call=0;call<64;call++){
                        boolean wasPreparing=runtime.preparing();int[] reads={0};
                        runtime.scan(p->{reads[0]++;return stone;},32,now);
                        assertTrue(reads[0]<=32);
                        if(!wasPreparing&&runtime.preparing())readyAt=now+200;
                    }
                    if(tick%20==0){
                        var summary=runtime.summary(now);
                        if(summary.correct()==3276800&&firstComplete<0)firstComplete=now;
                        if(firstComplete>=0){
                            assertEquals(3276800,summary.correct(),"Full freshness at "+now);
                            assertEquals(32768,runtime.selectedSummary(now).correct(),"Pinned map must not starve");
                        }
                    }
                }
                assertTrue(firstComplete>=0&&firstComplete<120000,"Initial whole-art coverage must not take minutes");
                System.out.println("Dense cadence: first complete="+firstComplete+"ms; continuous freshness verified through 400000ms");
            }
        });
    }
}
