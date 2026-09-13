package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MapStackRecognition {
    private MapStackRecognition() {
    }

    static Map<Integer, Match> resolve(
        List<Observation> observations,
        List<AutoFrameTemplate> templates,
        List<Known> known
    ) {
        Map<Integer, Observation> unique = new LinkedHashMap<>();
        for (Observation observation : observations) unique.putIfAbsent(observation.mapId(), observation);
        Map<String, AutoFrameTemplate> templateByKey = new HashMap<>();
        for (AutoFrameTemplate template : templates) templateByKey.put(key(template), template);

        Map<Integer, Match> result = new LinkedHashMap<>();
        for (Known value : known) {
            Observation observation = unique.get(value.mapId());
            AutoFrameTemplate template = templateByKey.get(value.groupKey());
            if (observation == null || template == null || value.tileIndex() < 0
                || value.tileIndex() >= template.tileHashes().size()) continue;
            int exactIndex = template.tileIndexForMapId(value.mapId());
            if (exactIndex >= 0 && exactIndex != value.tileIndex()) continue;
            String expected = template.tileHashes().get(value.tileIndex());
            if (value.tileHash() != null && !value.tileHash().equals(expected)) continue;
            if (observation.hash() != null && !observation.hash().equals(expected)) continue;
            result.put(value.mapId(), new Match(value.mapId(), template, value.tileIndex(), expected));
        }

        for (Observation observation : unique.values()) {
            if (result.containsKey(observation.mapId()) || observation.hash() == null) continue;
            List<Candidate> exactIds = candidatesByMapId(observation, templates);
            if (exactIds.size() == 1) put(result, observation, exactIds.getFirst());
        }

        for (Observation observation : unique.values()) {
            if (result.containsKey(observation.mapId()) || observation.hash() == null) continue;
            List<Candidate> candidates = candidatesByHash(observation.hash(), templates);
            if (candidates.size() == 1) put(result, observation, candidates.getFirst());
        }

        return Map.copyOf(result);
    }

    static boolean matchesCell(Observation observation, AutoFrameTemplate template, int tileIndex,
                               List<AutoFrameTemplate> templates, List<Known> known) {
        // Placement requires live pixels as well as identity; a cached binding alone is not enough.
        if (observation.hash() == null) return false;
        Match match = resolve(List.of(observation), templates, known).get(observation.mapId());
        return match != null && match.groupKey().equals(key(template)) && match.tileIndex() == tileIndex;
    }

    private static List<Candidate> candidatesByMapId(Observation observation, List<AutoFrameTemplate> templates) {
        List<Candidate> values = new ArrayList<>();
        for (AutoFrameTemplate template : templates) {
            int index = template.tileIndexForMapId(observation.mapId());
            if (index >= 0 && template.tileHashes().get(index).equals(observation.hash())) {
                values.add(new Candidate(template, index));
            }
        }
        return values;
    }

    private static List<Candidate> candidatesByHash(String hash, List<AutoFrameTemplate> templates) {
        List<Candidate> values = new ArrayList<>();
        for (AutoFrameTemplate template : templates) {
            for (int index = 0; index < template.tileHashes().size(); index++) {
                if (template.tileHashes().get(index).equals(hash)) values.add(new Candidate(template, index));
            }
        }
        return values;
    }

    private static void put(Map<Integer, Match> result, Observation observation, Candidate candidate) {
        result.put(observation.mapId(), new Match(
            observation.mapId(), candidate.template(), candidate.tileIndex(), observation.hash()
        ));
    }

    private static String key(AutoFrameTemplate template) {
        return template.artId() + "|" + template.versionId();
    }

    record Observation(int mapId, String hash) {
        Observation {
            if (mapId < 0) throw new IllegalArgumentException("Map ID must be non-negative.");
            if (hash != null && !MapColorFingerprint.isValid(hash)) {
                throw new IllegalArgumentException("Map fingerprint is invalid.");
            }
        }
    }

    record Known(int mapId, String groupKey, int tileIndex, String tileHash) {
    }

    record Match(int mapId, AutoFrameTemplate template, int tileIndex, String tileHash) {
        Match {
            Objects.requireNonNull(template, "template");
        }

        String groupKey() {
            return key(template);
        }

        int tileNumber() {
            return tileIndex + 1;
        }
    }

    private record Candidate(AutoFrameTemplate template, int tileIndex) {
    }
}
