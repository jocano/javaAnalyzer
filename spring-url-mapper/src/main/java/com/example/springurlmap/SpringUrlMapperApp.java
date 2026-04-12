package com.example.springurlmap;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Command-line utility: scan a Spring project for controller URL mappings, print a map, then
 * interactively match a URL and open the handler in IntelliJ IDEA.
 *
 * <p>Usage:
 * <pre>
 *   java -jar spring-url-mapper.jar [options]
 *   mvn -q exec:java -Dexec.mainClass=com.example.springurlmap.SpringUrlMapperApp
 *
 * Options:
 *   --root &lt;dir&gt;   Root folder of the Java/Spring project (default: user.dir)
 *   --print-only     Scan and print mappings, then exit (no interactive loop)
 * </pre>
 */
public final class SpringUrlMapperApp {

    private static final Pattern OPEN_CMD = Pattern.compile("^open\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        boolean printOnly = false;

        for (int i = 0; i < args.length; i++) {
            if ("--root".equals(args[i]) && i + 1 < args.length) {
                root = Paths.get(args[++i]).toAbsolutePath().normalize();
            } else if ("--print-only".equals(args[i])) {
                printOnly = true;
            } else if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                printHelp();
                return;
            }
        }

        System.out.println("Scanning Spring controllers under: " + root);
        Map<String, EndpointMapping> map = SpringControllerScanner.scan(root);
        if (map.isEmpty()) {
            System.out.println("No @Controller/@RestController mappings found (or no parseable .java files).");
            if (!printOnly) {
                System.out.println("Nothing to search; exiting.");
            }
            return;
        }

        printFullUrlMap(map);

        if (printOnly) {
            return;
        }

        runInteractiveLoop(map);
    }

    private static void printHelp() {
        System.out.println("""
                Spring URL mapper — lists Spring MVC controller URLs and opens handlers in IntelliJ.

                Options:
                  --root <path>   Project root (default: current working directory)
                  --print-only    Print the map and exit
                  -h, --help      This message

                Environment:
                  IDEA_BIN / IDEA_PATH   Full path to the IntelliJ `idea` launcher script

                After the map is printed, enter URL paths (with or without leading slash). Commands:
                  ..                   Reprint the full URL map (cached; no rescan)
                  quit | exit          Leave the program
                  open <n>             Open the n-th match from the last search in IntelliJ

                Tab suggests matching METHOD+path keys, commands, and open numbers after a search.
                At the url> prompt, ↑ / ↓ recall previous inputs (readline-style).

                Each match prints REST + one file:///…/File.java:line link — use IntelliJ’s Terminal
                and Cmd/Ctrl-click (macOS/Windows). Optional: SPRING_URL_MAPPER_USE_IDEA_PROTOCOL=1
                or SPRING_URL_MAPPER_USE_IDE_HTTP=1 for other link targets.
                Plain: SPRING_URL_MAPPER_PLAIN=1 or -Dspring.url.mapper.plain=true
                """);
    }

    private static void runInteractiveLoop(Map<String, EndpointMapping> map) throws IOException {
        List<EndpointMapping> all = new ArrayList<>(map.values());
        List<EndpointMapping> lastMatches = List.of();
        AtomicInteger lastSearchMatchCount = new AtomicInteger(0);

        System.out.println("Enter a URL path to find a controller (e.g. /api/users), or quit.");
        System.out.println("Type .. to show the full URL map again (from cache). ↑ / ↓ recall history.");
        System.out.println("Press Tab for suggestions (matching URLs, .., quit, open N). Ctrl+D exits.");

        try (Terminal terminal = TerminalBuilder.builder().jansi(true).build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new InteractiveCompleter(map, lastSearchMatchCount::get))
                    .history(new DefaultHistory())
                    .variable(LineReader.HISTORY_SIZE, 500)
                    .option(LineReader.Option.HISTORY_IGNORE_DUPS, true)
                    .option(LineReader.Option.AUTO_MENU_LIST, true)
                    .build();

            while (true) {
                String line;
                try {
                    line = reader.readLine("url> ");
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String low = line.toLowerCase(Locale.ROOT);
                if ("quit".equals(low) || "exit".equals(low)) {
                    break;
                }

                if ("..".equals(line)) {
                    System.out.println();
                    System.out.println();
                    printFullUrlMap(map);
                    continue;
                }

                Matcher openM = OPEN_CMD.matcher(line);
                if (openM.matches()) {
                    int n = Integer.parseInt(openM.group(1));
                    if (lastMatches.isEmpty()) {
                        System.out.println("  No previous search; enter a URL first.");
                        continue;
                    }
                    if (n < 1 || n > lastMatches.size()) {
                        System.out.println("  Pick a number between 1 and " + lastMatches.size() + ".");
                        continue;
                    }
                    EndpointMapping pick = lastMatches.get(n - 1);
                    Optional<String> err = IntellijOpener.open(pick.sourceFile(), pick.line());
                    if (err.isPresent()) {
                        System.out.println("  " + err.get());
                    } else {
                        System.out.println("  Started IntelliJ for " + pick.sourceFile() + " (line " + pick.line() + ").");
                    }
                    continue;
                }

                List<EndpointMapping> matches = UrlMatcher.rankMatches(line, all);
                System.out.println();
                System.out.println();
                if (matches.isEmpty()) {
                    System.out.println("  (no matching controller path patterns)");
                    lastMatches = List.of();
                    lastSearchMatchCount.set(0);
                    continue;
                }
                lastMatches = matches;
                lastSearchMatchCount.set(matches.size());
                int show = Math.min(matches.size(), 15);
                System.out.println("  Top matches — REST + file:///…:line (IntelliJ Terminal):");
                for (int i = 0; i < show; i++) {
                    EndpointMapping em = matches.get(i);
                    System.out.println("    ─ " + (i + 1) + " ─");
                    System.out.println(em.formatSearchResultBlock("      ", false));
                }
                if (matches.size() > show) {
                    System.out.println("    … +" + (matches.size() - show) + " more");
                }
                System.out.println("  In IntelliJ’s Terminal: Cmd/Ctrl-click the file:///…:line link, or type: open 1");
            }
        }
    }

    /** Same listing as at startup: full sorted URL → controller map (uses cached {@code map}). */
    private static void printFullUrlMap(Map<String, EndpointMapping> map) {
        System.out.println();
        System.out.println("— URL map (" + map.size() + " entries) —");
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort(String::compareTo);
        for (String k : keys) {
            EndpointMapping em = map.get(k);
            System.out.println(em.formatSearchResultBlock("  ", true));
            System.out.println();
        }
    }
}
