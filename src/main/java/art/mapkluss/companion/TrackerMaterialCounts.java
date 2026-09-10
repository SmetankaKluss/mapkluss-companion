package art.mapkluss.companion;

import java.util.*;

/** Adapt registry IDs to the existing resource-session keys, without changing saved data. */
final class TrackerMaterialCounts {
    static Map<String,Integer> forSession(List<BuildSessionMaterial> materials,Map<String,Integer> scanned) {
        var result=new HashMap<String,Integer>();
        if(materials==null)return result;
        for(var material:materials) {
            var name=material.nbtName();
            var registry=name.contains(":")?name:"minecraft:"+name;
            result.put(name,Math.max(0,Math.min(material.count(),scanned.getOrDefault(registry,0))));
        }
        return result;
    }
}
