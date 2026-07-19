package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

        boolean changed;
        do {
            changed = false;
            Set<String> presentGroups = new LinkedHashSet<>();
            for (Match match : result.values()) presentGroups.add(match.groupKey());
            for (Observation observation : unique.values()) {
                if (result.containsKey(observation.mapId()) || observation.hash() == null) continue;
                List<Candidate> candidates = candidatesByHash(observation.hash(), templates).stream()
                    .filter(candidate -> presentGroups.contains(key(candidate.template())))
                    .toList();
                Set<String> candidateGroups = new LinkedHashSet<>();
                for (Candidate candidate : candidates) candidateGroups.add(key(candidate.template()));
                if (candidateGroups.size() != 1) continue;
                String selectedGroup = candidateGroups.iterator().next();
                List<Candidate> oneGroup = candidates.stream()
                    .filter(candidate -> key(candidate.template()).equals(selectedGroup))
                    .toList();
                Candidate selected = exactOrSingle(observation, oneGroup);
                if (selected != null) {
                    put(result, observation, selected);
                    changed = true;
                }
            }
        } while (changed);

        return Map.copyOf(result);
    }

    private static Candidate exactOrSingle(Observation observation, List<Candidate> candidates) {
        List<Candidate> exact = candidates.stream().filter(candidate ->
            candidate.template().tileIndexForMapId(observation.mapId()) == candidate.tileIndex()
        ).toList();
        if (exact.size() == 1) return exact.getFirst();
        return candidates.size() == 1 ? candidates.getFirst() : null;
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
