package com.example.springurlmap;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

/**
 * Tab-completion for URL fragments (against cached {@code METHOD path} keys), commands, and
 * {@code open <n>} after a search.
 */
public final class InteractiveCompleter implements Completer {

    private static final int MAX_URL_CANDIDATES = 60;

    private final List<String> sortedKeys;
    private final IntSupplier lastSearchMatchCount;

    public InteractiveCompleter(Map<String, EndpointMapping> map, IntSupplier lastSearchMatchCount) {
        this.sortedKeys = map.keySet().stream().sorted(String::compareTo).collect(Collectors.toList());
        this.lastSearchMatchCount = lastSearchMatchCount;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        int cur = Math.min(line.cursor(), line.line().length());
        String soFar = line.line().substring(0, cur);
        String trimmed = soFar.trim();

        if (trimmed.regionMatches(true, 0, "open", 0, 4)) {
            completeOpen(trimmed, candidates);
            return;
        }

        completeCommands(trimmed, candidates);
        completeUrls(trimmed, candidates);
    }

    private void completeOpen(String trimmed, List<Candidate> candidates) {
        String rest = trimmed.length() > 4 ? trimmed.substring(4).trim() : "";
        if (!rest.isEmpty() && !rest.chars().allMatch(Character::isDigit)) {
            return;
        }
        int n = lastSearchMatchCount.getAsInt();
        if (n <= 0) {
            return;
        }
        int cap = Math.min(n, 50);
        for (int i = 1; i <= cap; i++) {
            candidates.add(new Candidate("open " + i));
        }
    }

    private void completeCommands(String trimmed, List<Candidate> candidates) {
        addIfPrefix(candidates, trimmed, "..");
        addIfPrefix(candidates, trimmed, "quit");
        addIfPrefix(candidates, trimmed, "exit");
    }

    private static void addIfPrefix(List<Candidate> candidates, String typed, String command) {
        String t = typed.toLowerCase(Locale.ROOT);
        String c = command.toLowerCase(Locale.ROOT);
        if (typed.isEmpty() || c.startsWith(t)) {
            candidates.add(new Candidate(command));
        }
    }

    private void completeUrls(String typed, List<Candidate> candidates) {
        if (typed.startsWith("open")) {
            return;
        }
        int budget = Math.max(0, MAX_URL_CANDIDATES - candidates.size());
        if (budget <= 0) {
            return;
        }
        String needle = typed.toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            for (int i = 0; i < sortedKeys.size() && i < budget; i++) {
                candidates.add(new Candidate(sortedKeys.get(i)));
            }
            return;
        }
        List<Scored> scored = new ArrayList<>();
        for (String key : sortedKeys) {
            int s = score(key, needle);
            if (s > 0) {
                scored.add(new Scored(s, key));
            }
        }
        scored.sort(Comparator.comparingInt((Scored x) -> -x.score).thenComparing(x -> x.key));
        for (int i = 0; i < scored.size() && i < budget; i++) {
            candidates.add(new Candidate(scored.get(i).key));
        }
    }

    private static int score(String key, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.startsWith(needle)) {
            return 4;
        }
        int space = key.indexOf(' ');
        String pathPart = space >= 0 ? key.substring(space + 1).toLowerCase(Locale.ROOT) : k;
        if (pathPart.startsWith(needle)) {
            return 3;
        }
        if (k.contains(needle)) {
            return 2;
        }
        return 0;
    }

    private record Scored(int score, String key) {}
}
