package com.example.springurlmap;

import java.util.ArrayList;
import java.util.List;

final class UrlPaths {

    private UrlPaths() {}

    static String normalizePath(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "/";
        }
        String s = raw.trim();
        if (!s.startsWith("/")) {
            s = "/" + s;
        }
        StringBuilder out = new StringBuilder();
        boolean slash = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '/') {
                if (!slash) {
                    out.append(c);
                }
                slash = true;
            } else {
                slash = false;
                out.append(c);
            }
        }
        String r = out.toString();
        if (r.length() > 1 && r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r.isEmpty() ? "/" : r;
    }

    static String join(String typeLevel, String methodLevel) {
        String a = typeLevel == null ? "" : typeLevel.trim();
        String b = methodLevel == null ? "" : methodLevel.trim();
        if (a.isEmpty() && b.isEmpty()) {
            return "/";
        }
        if (a.isEmpty()) {
            return normalizePath(b);
        }
        if (b.isEmpty()) {
            return normalizePath(a);
        }
        if (a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }
        if (!a.startsWith("/")) {
            a = "/" + a;
        }
        if (!b.startsWith("/")) {
            b = "/" + b;
        }
        return normalizePath(a + b);
    }

    static List<String> cartesianPaths(List<String> prefixes, List<String> suffixes) {
        List<String> pfx = prefixes == null || prefixes.isEmpty() ? List.of("") : prefixes;
        List<String> sfx = suffixes == null || suffixes.isEmpty() ? List.of("") : suffixes;
        List<String> out = new ArrayList<>(pfx.size() * sfx.size());
        for (String p : pfx) {
            for (String s : sfx) {
                out.add(join(p, s));
            }
        }
        return out;
    }
}
