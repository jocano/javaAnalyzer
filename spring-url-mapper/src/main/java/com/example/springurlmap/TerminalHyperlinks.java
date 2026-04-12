package com.example.springurlmap;

import java.util.Locale;

/**
 * <a href="https://gist.github.com/egmontkob/eb114294efbcd5adb1944c9f3bfccdfe">OSC 8</a> hyperlinks
 * for terminals that support them (IntelliJ, iTerm2, Windows Terminal, WezTerm, VS Code, etc.).
 */
public final class TerminalHyperlinks {

    private static final char ESC = '\u001B';

    private TerminalHyperlinks() {}

    /**
     * Whether to emit OSC 8 sequences (disable with {@code SPRING_URL_MAPPER_PLAIN=1} or
     * {@code -Dspring.url.mapper.plain=true}).
     */
    public static boolean useHyperlinks() {
        if (Boolean.getBoolean("spring.url.mapper.plain")) {
            return false;
        }
        if (truthyEnv("SPRING_URL_MAPPER_PLAIN")) {
            return false;
        }
        if (truthyEnv("SPRING_URL_MAPPER_HYPERLINK")) {
            return true;
        }
        if (System.console() != null) {
            return true;
        }
        String te = System.getenv("TERMINAL_EMULATOR");
        if (te != null && te.toLowerCase(Locale.ROOT).contains("jetbrains")) {
            return true;
        }
        if (System.getenv("WT_SESSION") != null) {
            return true;
        }
        String tp = System.getenv("TERM_PROGRAM");
        if (tp != null) {
            String t = tp.toLowerCase(Locale.ROOT);
            if (t.contains("iterm") || t.contains("vscode") || t.contains("wez")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wraps visible text in an OSC 8 hyperlink to {@code url}. If hyperlinks are disabled, returns
     * {@code visibleText} unchanged.
     * <p>In <strong>IntelliJ’s built-in terminal</strong>, {@code file:///…:line} links are detected when
     * printed as plain text — OSC 8 would prevent that, so for {@code file:} URLs we skip wrapping and
     * return {@code visibleText} as-is (callers should pass the full {@code file:///…:line} string).
     */
    public static String link(String url, String visibleText) {
        if (url == null || url.isEmpty()) {
            return visibleText;
        }
        if (!useHyperlinks()) {
            return visibleText != null && !visibleText.isEmpty() ? visibleText : url;
        }
        if (url.regionMatches(true, 0, "file:", 0, 5) && isJetBrainsTerminal()) {
            return visibleText != null && !visibleText.isEmpty() ? visibleText : url;
        }
        // OSC 8: ESC ] 8 ; ; URL ST ... ST  with ST = ESC \
        String show = visibleText != null && !visibleText.isEmpty() ? visibleText : url;
        return ESC + "]8;;" + url + ESC + "\\" + show + ESC + "]8;;" + ESC + "\\";
    }

    /** JediTerm / IntelliJ embedded terminal sets {@code TERMINAL_EMULATOR} accordingly. */
    public static boolean isJetBrainsTerminal() {
        String te = System.getenv("TERMINAL_EMULATOR");
        return te != null && te.toLowerCase(Locale.ROOT).contains("jetbrains");
    }

    private static boolean truthyEnv(String name) {
        String v = System.getenv(name);
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return s.equals("1") || s.equals("true") || s.equals("yes");
    }
}
