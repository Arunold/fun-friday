package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.TrackMap.Cell;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Flock;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Heading;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the curated level maps from {@code src/main/resources} and validates them.
 *
 * <p>Format: a header of {@code key: value} lines ({@code name}, {@code heading} = N, E, S or W), a
 * line {@code ---}, then the rows of the map. Legend: {@code .} grass, {@code #} building, {@code
 * T} tree, {@code w} water, {@code r} road, {@code ~} road in rain, {@code t} road in a tunnel,
 * {@code P} parking lot, {@code E} the CEO's car, {@code S} start, {@code C} cone, {@code K}
 * coffee, {@code G} geese crossing. Start and hazards lie on road; in a rain or tunnel stretch they
 * take on its surface.
 *
 * <p>Validation: rectangular, known characters, exactly one start, at least one parking tile and a
 * CEO car, and the parking lot reachable by road from the start.
 */
final class TrackLoader {

    /** The level files in campaign order: 1 Campus Loop, 2 Downtown, 3 Business Park. */
    static final List<String> LEVEL_FILES = List.of("level-1.txt", "level-2.txt", "level-3.txt");

    private static final char START = 'S';
    private static final char CONE = 'C';
    private static final char COFFEE = 'K';
    private static final char GEESE = 'G';

    private TrackLoader() {}

    /** The three shipped levels, parsed once. */
    static List<TrackMap> campaign() {
        return Campaign.LEVELS;
    }

    /** Loads and validates one level file of this package. */
    static TrackMap load(String resource) {
        try (InputStream in = TrackLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing level file " + resource);
            }
            return parse(resource, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read " + resource, e);
        }
    }

    /**
     * Parses and validates a map.
     *
     * @param source name used in error messages
     * @param text the map file's content
     * @throws IllegalArgumentException when the map is invalid
     */
    static TrackMap parse(String source, String text) {
        List<String> lines = text.lines().toList();
        int separator = lines.indexOf("---");
        if (separator < 0) {
            throw invalid(source, "the header must end with a line '---'");
        }
        Map<String, String> header = header(source, lines.subList(0, separator));
        List<String> rows = new ArrayList<>(lines.subList(separator + 1, lines.size()));
        while (!rows.isEmpty() && rows.getLast().isBlank()) {
            rows.removeLast();
        }
        String name = header.get("name");
        if (name == null || name.isBlank()) {
            throw invalid(source, "the header needs a name");
        }
        Heading heading = heading(source, header.get("heading"));
        return new Parser(source, rows).build(name.strip(), heading);
    }

    private static Map<String, String> header(String source, List<String> lines) {
        Map<String, String> header = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 1) {
                throw invalid(source, "bad header line '" + line + "'");
            }
            header.put(
                    line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                    line.substring(colon + 1).strip());
        }
        return header;
    }

    private static Heading heading(String source, String value) {
        if (value == null) {
            throw invalid(source, "the header needs a heading (N, E, S or W)");
        }
        try {
            return Heading.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw invalid(source, "heading must be N, E, S or W, not '" + value + "'");
        }
    }

    private static IllegalArgumentException invalid(String source, String message) {
        return new IllegalArgumentException(source + ": " + message);
    }

    /** Turns the rows into a validated map. */
    private static final class Parser {
        private final String source;
        private final List<String> rows;
        private final int width;
        private final int height;
        private final char[] raw;
        private final Tile[] tiles;

        private Parser(String source, List<String> rows) {
            this.source = source;
            this.rows = rows;
            if (rows.isEmpty()) {
                throw invalid(source, "the map has no rows");
            }
            this.width = rows.getFirst().length();
            this.height = rows.size();
            this.raw = new char[width * height];
            this.tiles = new Tile[width * height];
        }

        TrackMap build(String name, Heading heading) {
            readCharacters();
            List<Cell> starts = cells(START);
            if (starts.size() != 1) {
                throw invalid(source, "needs exactly one start 'S', found " + starts.size());
            }
            resolveHazardGround();
            if (count(Tile.PARKING) == 0) {
                throw invalid(source, "needs at least one parking tile 'P'");
            }
            if (count(Tile.CEO_CAR) == 0) {
                throw invalid(source, "needs the CEO's car 'E'");
            }
            Cell start = starts.getFirst();
            if (!parkingReachable(start)) {
                throw invalid(source, "the parking lot can't be reached by road from the start");
            }
            return new TrackMap(
                    name,
                    width,
                    height,
                    tiles,
                    start,
                    heading,
                    cells(CONE),
                    cells(COFFEE),
                    flocks());
        }

        private void readCharacters() {
            for (int y = 0; y < height; y++) {
                String row = rows.get(y);
                if (row.length() != width) {
                    throw invalid(
                            source,
                            "row "
                                    + (y + 1)
                                    + " has "
                                    + row.length()
                                    + " columns, expected "
                                    + width);
                }
                for (int x = 0; x < width; x++) {
                    char symbol = row.charAt(x);
                    Tile tile = Tile.of(symbol);
                    if (tile == null && !isHazard(symbol)) {
                        throw invalid(
                                source,
                                "unknown character '"
                                        + symbol
                                        + "' at column "
                                        + (x + 1)
                                        + ", row "
                                        + (y + 1));
                    }
                    raw[y * width + x] = symbol;
                    tiles[y * width + x] = tile;
                }
            }
        }

        private static boolean isHazard(char symbol) {
            return symbol == START || symbol == CONE || symbol == COFFEE || symbol == GEESE;
        }

        /**
         * Start and hazard cells are road; inside a rain or tunnel stretch they take its surface.
         */
        private void resolveHazardGround() {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (tiles[y * width + x] == null) {
                        tiles[y * width + x] = surfaceAround(x, y);
                    }
                }
            }
        }

        private Tile surfaceAround(int x, int y) {
            boolean tunnel = false;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] step : steps) {
                char neighbour = charAt(x + step[0], y + step[1]);
                if (neighbour == Tile.RAIN.symbol()) {
                    return Tile.RAIN;
                }
                tunnel |= neighbour == Tile.TUNNEL.symbol();
            }
            return tunnel ? Tile.TUNNEL : Tile.ROAD;
        }

        private char charAt(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height ? raw[y * width + x] : '#';
        }

        private boolean parkingReachable(Cell start) {
            boolean[] seen = new boolean[width * height];
            Deque<Cell> queue = new ArrayDeque<>();
            queue.add(start);
            seen[start.y() * width + start.x()] = true;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                Cell cell = queue.removeFirst();
                if (tiles[cell.y() * width + cell.x()] == Tile.PARKING) {
                    return true;
                }
                for (int[] step : steps) {
                    int nx = cell.x() + step[0];
                    int ny = cell.y() + step[1];
                    if (nx >= 0
                            && ny >= 0
                            && nx < width
                            && ny < height
                            && !seen[ny * width + nx]
                            && tiles[ny * width + nx].paved()) {
                        seen[ny * width + nx] = true;
                        queue.addLast(new Cell(nx, ny));
                    }
                }
            }
            return false;
        }

        /** Connected groups of geese cells (4-neighbourhood), in reading order. */
        private List<Flock> flocks() {
            boolean[] taken = new boolean[width * height];
            List<Flock> flocks = new ArrayList<>();
            for (Cell first : cells(GEESE)) {
                if (taken[first.y() * width + first.x()]) {
                    continue;
                }
                List<Cell> group = new ArrayList<>();
                Deque<Cell> queue = new ArrayDeque<>(List.of(first));
                taken[first.y() * width + first.x()] = true;
                while (!queue.isEmpty()) {
                    Cell cell = queue.removeFirst();
                    group.add(cell);
                    for (Cell next : neighbours(cell)) {
                        int index = next.y() * width + next.x();
                        if (!taken[index] && raw[index] == GEESE) {
                            taken[index] = true;
                            queue.addLast(next);
                        }
                    }
                }
                flocks.add(new Flock(flocks.size(), group));
            }
            return flocks;
        }

        private List<Cell> neighbours(Cell cell) {
            List<Cell> list = new ArrayList<>(4);
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] step : steps) {
                int nx = cell.x() + step[0];
                int ny = cell.y() + step[1];
                if (nx >= 0 && ny >= 0 && nx < width && ny < height) {
                    list.add(new Cell(nx, ny));
                }
            }
            return list;
        }

        private List<Cell> cells(char symbol) {
            List<Cell> cells = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (raw[y * width + x] == symbol) {
                        cells.add(new Cell(x, y));
                    }
                }
            }
            return cells;
        }

        private int count(Tile kind) {
            int count = 0;
            for (Tile tile : tiles) {
                if (tile == kind) {
                    count++;
                }
            }
            return count;
        }
    }

    /** Lazy holder: the shipped levels are parsed on first use and shared by every game. */
    private static final class Campaign {
        private static final List<TrackMap> LEVELS =
                LEVEL_FILES.stream().map(TrackLoader::load).toList();

        private Campaign() {}
    }
}
