package art.mapkluss.companion;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildRemoteEvidenceTest {
    private static final String ID="11111111-1111-4111-8111-111111111111";
    private static final LiveBuildGroupController.Source SOURCE=new LiveBuildGroupController.Source("a".repeat(64),1,1);
    private static LiveBuildRemoteEvidence.Key key(){
        var placement=new LiveBuildSharedPlacement(0,1,"b".repeat(64),-1,1,ID,"minecraft:overworld",
            new LiveBuildProgress.Position(0,0,0),LiveBuildTransform.NONE);
        return new LiveBuildRemoteEvidence.Key(new LiveBuildGroupController.Adoption(1,ID,SOURCE,placement),0);
    }
    private static JsonObject response(LiveBuildRemoteEvidence.Key key,long membership,String packed,boolean unchanged){
        var value=key.input();value.addProperty("revision",1);value.addProperty("membership_revision",membership);
        if(unchanged)value.addProperty("unchanged",true);else value.addProperty("packed",packed);
        var array=new JsonArray();array.add(value);var build=new JsonObject();build.add("pages",array);
        var result=new JsonObject();result.add("build",build);return result;
    }
    private static LiveBuildProgress.Observation local(LiveBuildProgress.Status status,long time){return new LiveBuildProgress.Observation(status,status,time);}
    @Test void unchangedDoesNotFreshenAndNewerLocalBreakWins(){
        var cache=new LiveBuildRemoteEvidence();var key=key();
        cache.accept(List.of(key),response(key,2,"ACk=",false),1000);
        var unknown=local(LiveBuildProgress.Status.UNKNOWN,-1);
        assertEquals(500,cache.merge(key.adoption(),0,unknown,2000).observedAt());
        var broken=local(LiveBuildProgress.Status.MISSING,600);
        assertEquals(broken,cache.merge(key.adoption(),0,broken,2000));
        cache.accept(List.of(key),response(key,2,null,true),100000);
        assertEquals(500,cache.merge(key.adoption(),0,unknown,100000).observedAt());
        assertEquals(unknown,cache.merge(key.adoption(),0,unknown,120501));
        assertEquals(1,cache.request(key).get("known_revision").getAsInt());
    }
    @Test void equalTimeIsConservativeAndRevocationDropsCache(){
        var cache=new LiveBuildRemoteEvidence();var key=key();
        cache.accept(List.of(key),response(key,2,"AAE=",false),1000);
        var wrong=local(LiveBuildProgress.Status.WRONG,1000);
        assertEquals(wrong,cache.merge(key.adoption(),0,wrong,1000));
        assertThrows(IllegalArgumentException.class,()->cache.accept(List.of(key),response(key,3,null,true),1100));
        cache.retain(Set.of());assertFalse(cache.request(key).has("known_revision"));
        assertThrows(IllegalArgumentException.class,()->cache.accept(List.of(key),response(key,2,null,true),1200));
    }
    @Test void rejectsWrongIdentityAndNeverExportsDisplayEvidence(){
        var cache=new LiveBuildRemoteEvidence();var key=key();var value=response(key,2,"AAE=",false);
        value.getAsJsonObject("build").getAsJsonArray("pages").get(0).getAsJsonObject().addProperty("phase",10);
        assertThrows(IllegalArgumentException.class,()->cache.accept(List.of(key),value,1000));
        assertFalse(cache.request(key).has("known_revision"));
        cache.accept(List.of(key),response(key,2,"AAE=",false),1000);
        var state=new LiveBuildProgress.State("minecraft:stone",Map.of());
        var identity=new LiveBuildProgress.Identity("b".repeat(64),"private-world","minecraft:overworld",new LiveBuildProgress.Position(0,0,0),1);
        var progress=new LiveBuildProgress(identity,List.of(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),state)));
        try{
            progress.displayEvidence((cell,local,now)->cache.merge(key.adoption(),cell,local,now));
            assertEquals(LiveBuildProgress.Status.CORRECT,progress.displayObservation(0,1000,120000).status());
            assertEquals(LiveBuildProgress.Status.UNKNOWN,progress.observation(0,1000,120000).status());
            assertFalse(progress.exportPage(0,1000,0).hasObservations());
            progress.displayEvidence(null);
            assertEquals(LiveBuildProgress.Status.UNKNOWN,progress.displayObservation(0,1000,120000).status());
        }finally{progress.close();}
    }
}
