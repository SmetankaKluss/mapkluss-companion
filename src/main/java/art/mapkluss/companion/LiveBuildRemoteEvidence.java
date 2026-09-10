package art.mapkluss.companion;

import com.google.gson.JsonObject;
import java.util.*;

/** Display-only pages. Never inserted into local scan observations or persistence. */
public final class LiveBuildRemoteEvidence {
    private static final LiveBuildProgress.Status[] STATES=LiveBuildProgress.Status.values();
    public record Key(LiveBuildGroupController.Adoption adoption,int page) {
        public Key {
            if(adoption==null||page<0||page>=(adoption.placement().cellCount()+4095)/4096)
                throw new IllegalArgumentException("Invalid remote page");
        }
        public int cells(){return Math.min(4096,adoption.placement().cellCount()-page*4096);}
        public JsonObject input(){
            var p=adoption.placement();var value=new JsonObject();
            value.addProperty("tile",p.tile());value.addProperty("page",page);
            value.addProperty("placement_revision",p.revision());value.addProperty("source_sha256",adoption.source().sha256());
            value.addProperty("target_sha256",p.targetSha256());value.addProperty("phase",p.phase());
            value.addProperty("world_binding",p.worldBinding());return value;
        }
    }
    private record Entry(long revision,long membership,LiveBuildEvidenceCodec.Page page) { }
    private final Map<Key,Entry> pages=new HashMap<>();
    private long membership=-1;
    public long membership(){return membership;}
    public void clear(){pages.clear();membership=-1;}
    public void retain(Set<LiveBuildGroupController.Adoption> accepted){pages.keySet().removeIf(k->!accepted.contains(k.adoption()));}
    public JsonObject request(Key key){
        var value=key.input();var old=pages.get(key);
        if(old!=null){value.addProperty("known_revision",old.revision());value.addProperty("membership_revision",old.membership());}
        return value;
    }
    /** Validate the complete batch before making any of it visible. */
    public void accept(List<Key> keys,JsonObject response,long started){
        var values=response.getAsJsonObject("build").getAsJsonArray("pages");
        if(values==null||values.size()!=keys.size()||keys.size()>32)throw new IllegalArgumentException("Wrong remote batch");
        var updates=new HashMap<Key,Entry>();long batchMembership=-1;
        for(int i=0;i<keys.size();i++){
            var key=keys.get(i);var value=values.get(i).getAsJsonObject();
            for(var field:key.input().entrySet())if(!field.getValue().equals(value.get(field.getKey())))
                throw new IllegalArgumentException("Different remote identity");
            long revision=LiveBuildSharedPlacement.integer(value,"revision");
            long member=LiveBuildSharedPlacement.integer(value,"membership_revision");
            if(revision<0||member<0||(batchMembership>=0&&member!=batchMembership)||member<membership)
                throw new IllegalArgumentException("Invalid remote revision");
            batchMembership=member;var old=pages.get(key);
            if(value.has("unchanged")){
                if(!value.get("unchanged").isJsonPrimitive()||!value.getAsJsonPrimitive("unchanged").isBoolean()
                    ||!value.get("unchanged").getAsBoolean()||old==null||old.revision()!=revision||old.membership()!=member)
                    throw new IllegalArgumentException("Invalid unchanged page");
                updates.put(key,old);
            }else{
                if(old!=null&&old.membership()==member&&revision<old.revision())throw new IllegalArgumentException("Old remote page");
                updates.put(key,new Entry(revision,member,LiveBuildEvidenceCodec.download(value.get("packed").getAsString(),key.cells(),started)));
            }
        }
        if(batchMembership!=membership){pages.clear();membership=batchMembership;}
        pages.putAll(updates);
        // The backend limits a group to eight million cells; do not retain beyond that bound.
        if(pages.size()>2052){clear();throw new IllegalArgumentException("Remote cache limit");}
    }
    public LiveBuildProgress.Observation merge(LiveBuildGroupController.Adoption adoption,int cell,
                                               LiveBuildProgress.Observation local,long now){
        var entry=pages.get(new Key(adoption,cell/4096));if(entry==null)return local;
        var page=entry.page();int index=cell%4096;int state=page.state(index,now);
        if(state<1||state>3)return local;
        long time=page.observedAt(index);
        if(local.observedAt()>time)return local;
        if(local.observedAt()==time&&local.lastKnown().ordinal()>state)return local;
        var status=STATES[state];
        return new LiveBuildProgress.Observation(status,status,time);
    }
}
