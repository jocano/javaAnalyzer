package com.example.springurlmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches a user-provided URL path against registered Spring path patterns ({@code {id}} segments).
 */
public final class UrlMatcher {

    private static final Pattern LEADING_HTTP_METHOD =
            Pattern.compile("^(?i)(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|\\*)\\s+(/.+)$");

    private UrlMatcher() {}

    public static List<EndpointMapping> rankMatches(String userInput, Iterable<EndpointMapping> all) {
        String path = normalizeUserPath(userInput);
        List<Scored> scored = new ArrayList<>();
        for (EndpointMapping em : all) {
            int score = scoreMatch(path, em.fullPath());
            if (score >= 0) {
                scored.add(new Scored(score, em));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> -s.score).thenComparing(s -> s.em.key()));
        return scored.stream().map(s -> s.em).toList();
    }

    private record Scored(int score, EndpointMapping em) {}

    static String normalizeUserPath(String raw) {
        if (raw == null) {
            return "/";
        }
        String s = raw.trim();
        var verbPath = LEADING_HTTP_METHOD.matcher(s);
        if (verbPath.matches()) {
            s = verbPath.group(2);
        }
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        int h = s.indexOf('#');
        if (h >= 0) {
            s = s.substring(0, h);
        }
        return UrlPaths.normalizePath(s);
    }

    /**
     * @return negative if no match; higher is better (exact segment matches score more)
     */
    static int scoreMatch(String userPath, String patternPath) {
        String p = UrlPaths.normalizePath(patternPath);
        String u = UrlPaths.normalizePath(userPath);

        if ("**".equals(p) || "/*".equals(p)) {
            return 1;
        }

        String[] ps = p.split("/");
        String[] us = u.split("/");
        if (ps.length != us.length) {
            // Spring ant-style ** could extend — simple mode: length must match
            return -1;
        }
        int score = 0;
        for (int i = 0; i < ps.length; i++) {
            String segP = ps[i];
            String segU = us[i];
            if (segP.isEmpty() && segU.isEmpty()) {
                continue;
            }
            if (isPathVariable(segP) || "*".equals(segP) || "**".equals(segP)) {
                score += 1;
            } else if (segP.equalsIgnoreCase(segU)) {
                score += 4;
            } else {
                return -1;
            }
        }
        return score;
    }

    private static boolean isPathVariable(String seg) {
        return seg.startsWith("{") && seg.endsWith("}");
    }
}
