package com.example.funfridaygame.games.backseatdrivers;

import com.example.funfridaygame.games.backseatdrivers.Seating.Seat;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Cell;
import com.example.funfridaygame.games.backseatdrivers.TrackMap.Flock;
import com.example.funfridaygame.sdk.GameContext;
import com.example.funfridaygame.sdk.PlayerInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The secret state of a level for automated tests ({@code GET /api/e2e/rooms/{code}} with the e2e
 * profile): bus, roles per player (by id and by name), hazards with their state, the start and the
 * CEO's cars.
 */
final class DebugState {

    private DebugState() {}

    /** The level's debug state. */
    static Map<String, Object> of(GameContext ctx, LevelRun level, Seating seating) {
        Simulation sim = level.simulation();
        TrackMap map = sim.map();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("level", level.number());
        state.put("levelName", map.name());
        state.put("status", level.result() == null ? "DRIVING" : level.result().status());
        state.put("tick", level.tickCount());
        Bus bus = sim.bus();
        state.put(
                "bus",
                Map.of("x", bus.x(), "y", bus.y(), "heading", bus.heading(), "speed", bus.speed()));
        Map<String, List<String>> roles = new LinkedHashMap<>();
        Map<String, List<String>> rolesByName = new LinkedHashMap<>();
        for (Seat seat : seating.seats()) {
            List<String> controls = seat.controls().stream().map(Enum::name).toList();
            roles.put(seat.playerId(), controls);
            rolesByName.put(
                    ctx.player(seat.playerId()).map(PlayerInfo::name).orElse(seat.playerId()),
                    controls);
        }
        state.put("roles", roles);
        state.put("rolesByName", rolesByName);
        state.put("rolesVersion", seating.version());
        state.put("navigator", seating.navigator());
        state.put("nextShuffleAt", level.nextShuffleAt());
        state.put("shuffleWarning", level.shuffleWarning());
        state.put(
                "start",
                Map.of(
                        "x", map.start().x(),
                        "y", map.start().y(),
                        "heading", map.heading().name()));
        state.put("ceoCars", map.ceoCars().stream().map(Cell::toArray).toList());
        state.put("conesHit", sim.conesHit());
        state.put("coffees", sim.coffees());
        state.put("headlights", sim.headlights());
        state.put("hazards", hazards(sim));
        return state;
    }

    private static Map<String, Object> hazards(Simulation sim) {
        TrackMap map = sim.map();
        List<Map<String, Object>> cones = new ArrayList<>();
        for (int i = 0; i < map.cones().size(); i++) {
            Cell cell = map.cones().get(i);
            cones.add(Map.of("x", cell.x(), "y", cell.y(), "left", sim.coneLeft(i)));
        }
        List<Map<String, Object>> coffees = new ArrayList<>();
        for (int i = 0; i < map.coffees().size(); i++) {
            Cell cell = map.coffees().get(i);
            coffees.add(Map.of("x", cell.x(), "y", cell.y(), "left", sim.coffeeLeft(i)));
        }
        List<Map<String, Object>> geese = new ArrayList<>();
        for (Flock flock : map.geese()) {
            geese.add(
                    Map.of(
                            "i", flock.index(),
                            "state", sim.goose(flock.index()).name(),
                            "cells", flock.cells().stream().map(Cell::toArray).toList()));
        }
        return Map.of("cones", cones, "coffees", coffees, "geese", geese);
    }
}
