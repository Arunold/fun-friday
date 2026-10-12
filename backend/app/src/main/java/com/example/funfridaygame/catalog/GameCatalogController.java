package com.example.funfridaygame.catalog;

import com.example.funfridaygame.runtime.SettingsResolver;
import com.example.funfridaygame.sdk.GameDefinition;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.SettingDef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST: the game catalog including each game's settings schema. */
@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Installed games and their settings")
public class GameCatalogController {

    private final GameCatalog catalog;

    public GameCatalogController(GameCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * A game in the catalog.
     *
     * @param id game id
     * @param name display name
     * @param description one-sentence pitch
     * @param icon emoji
     * @param minPlayers players needed to start
     * @param maxPlayers player limit
     * @param difficulty EASY, MEDIUM or HARD
     * @param rules how to play
     * @param settings the settings form
     * @param defaults default value per setting
     */
    public record GameSummary(
            String id,
            String name,
            String description,
            String icon,
            int minPlayers,
            int maxPlayers,
            GameInfo.Difficulty difficulty,
            List<String> rules,
            List<SettingDef> settings,
            Map<String, Object> defaults) {

        static GameSummary of(GameDefinition definition) {
            GameInfo info = definition.info();
            return new GameSummary(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.icon(),
                    info.minPlayers(),
                    info.maxPlayers(),
                    info.difficulty(),
                    info.rules(),
                    definition.settings(),
                    SettingsResolver.defaults(definition));
        }
    }

    @GetMapping
    @Operation(summary = "List all games with their settings schema")
    public List<GameSummary> games() {
        return catalog.all().stream().map(GameSummary::of).toList();
    }
}
