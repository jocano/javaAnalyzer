package com.example.springurlmap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tries to open a file at a given line in JetBrains IntelliJ IDEA via the command-line launcher.
 */
public final class IntellijOpener {

    private IntellijOpener() {}

    /**
     * Link target for terminal output. Default is {@code file:///absolute/path/File.java:line},
     * which IntelliJ’s own terminal (macOS and Windows) recognizes for Cmd/Ctrl-click navigation.
     * <p>Alternatives (opt-in):
     * <ul>
     *   <li>{@code SPRING_URL_MAPPER_USE_IDEA_PROTOCOL=1} — {@code idea://open?file=...&line=...&column=1}</li>
     *   <li>{@code SPRING_URL_MAPPER_USE_IDE_HTTP=1} — {@code http://127.0.0.1:63342/api/file?...} (IDE must be running;
     *       override port with {@code IDEA_HTTP_PORT})</li>
     * </ul>
     */
    public static String openFileAtLineUrl(Path file, int line) {
        if (useIdeHttpApiForLinks()) {
            return buildIdeHttpOpenUrl(file, line);
        }
        if (useIdeaProtocolForLinks()) {
            return buildIdeaProtocolOpenUrl(file, line);
        }
        return fileUriWithLine(file, line);
    }

    /**
     * {@code file:///…/Source.java:42} form (ASCII URI + {@code :line} suffix) for IntelliJ Terminal
     * clickable links.
     */
    public static String fileUriWithLine(Path file, int line) {
        Path abs = file.toAbsolutePath().normalize();
        URI uri = abs.toUri();
        int ln = Math.max(1, line);
        return uri.toASCIIString() + ":" + ln;
    }

    private static boolean useIdeHttpApiForLinks() {
        return truthyEnv("SPRING_URL_MAPPER_USE_IDE_HTTP") || truthyEnv("USE_IDE_HTTP");
    }

    private static boolean useIdeaProtocolForLinks() {
        return truthyEnv("SPRING_URL_MAPPER_USE_IDEA_PROTOCOL");
    }

    private static String ideHttpPort() {
        String p = firstNonBlank(System.getenv("IDEA_HTTP_PORT"), System.getenv("IJ_HTTP_PORT"));
        return (p == null || p.isBlank()) ? "63342" : p.trim();
    }

    private static String buildIdeHttpOpenUrl(Path file, int line) {
        String abs = file.toAbsolutePath().normalize().toString().replace('\\', '/');
        int ln = Math.max(1, line);
        return "http://127.0.0.1:" + ideHttpPort() + "/api/file?file=" + encodeFileQueryPath(abs)
                + "&line=" + ln + "&column=1&focused=true";
    }

    private static String buildIdeaProtocolOpenUrl(Path file, int line) {
        String abs = file.toAbsolutePath().normalize().toString().replace('\\', '/');
        int ln = Math.max(1, line);
        return "idea://open?file=" + encodeFileQueryPath(abs) + "&line=" + ln + "&column=1";
    }

    /**
     * Encodes a file path for use as a single query parameter value. Slashes stay literal so
     * {@code idea://} and the built-in server accept “real” paths; only reserved / non-ASCII
     * characters are percent-encoded.
     */
    static String encodeFileQueryPath(String absolutePath) {
        byte[] raw = absolutePath.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(raw.length + 8);
        for (byte b : raw) {
            int c = b & 0xff;
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '/'
                    || c == ':'
                    || c == '.'
                    || c == '_'
                    || c == '-'
                    || c == '~';
            if (allowed) {
                sb.append((char) c);
            } else if (c == ' ') {
                sb.append("%20");
            } else {
                sb.append(String.format("%%%02X", c));
            }
        }
        return sb.toString();
    }

    private static boolean truthyEnv(String name) {
        String v = System.getenv(name);
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("1") || s.equals("true") || s.equals("yes");
    }

    /**
     * Opens the file in IntelliJ at the given 1-based line, if a launcher command is found.
     *
     * @return empty if launched; otherwise a message explaining why not
     */
    public static Optional<String> open(Path file, int line) {
        Path absolute = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            return Optional.of("File does not exist: " + absolute);
        }

        List<String> cmd = resolveCommand(absolute, line);
        if (cmd == null) {
            return Optional.of(
                    "Could not find IntelliJ launcher. Install \"Create Command-line Launcher\" in IDEA "
                            + "(Tools), set IDEA_BIN or IDEA_PATH to the `idea` script, or add `idea` to PATH. "
                            + "On macOS, /Applications/IntelliJ IDEA.app/Contents/MacOS/idea is tried automatically.");
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        try {
            pb.start();
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of("Failed to start IntelliJ: " + e.getMessage());
        }
    }

    private static List<String> resolveCommand(Path absolute, int line) {
        String env = firstNonBlank(System.getenv("IDEA_BIN"), System.getenv("IDEA_PATH"));
        if (env != null) {
            Path ideaPath = Path.of(env.trim());
            if (Files.isExecutable(ideaPath)) {
                return List.of(ideaPath.toString(), "--line", String.valueOf(line), absolute.toString());
            }
        }

        // PATH: idea
        Path ideaFromPath = findOnPath("idea");
        if (ideaFromPath != null) {
            return List.of(ideaFromPath.toString(), "--line", String.valueOf(line), absolute.toString());
        }

        List<Path> macCandidates = new ArrayList<>();
        macCandidates.add(Path.of("/Applications/IntelliJ IDEA.app/Contents/MacOS/idea"));
        macCandidates.add(Path.of("/Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea"));
        macCandidates.add(Path.of("/Applications/IntelliJ IDEA Ultimate.app/Contents/MacOS/idea"));

        for (Path p : macCandidates) {
            if (Files.isExecutable(p)) {
                return List.of(p.toString(), "--line", String.valueOf(line), absolute.toString());
            }
        }

        return null;
    }

    private static Path findOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (String dir : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
