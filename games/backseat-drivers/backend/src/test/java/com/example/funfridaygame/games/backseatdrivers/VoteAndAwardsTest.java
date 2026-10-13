package com.example.funfridaygame.games.backseatdrivers;

import static com.example.funfridaygame.games.backseatdrivers.TestGames.holder;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.navigator;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.press;
import static com.example.funfridaygame.games.backseatdrivers.TestGames.tap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.funfridaygame.runtime.GameResult;
import com.example.funfridaygame.runtime.RunPhase;
import com.example.funfridaygame.sdk.Award;
import com.example.funfridaygame.testkit.GameHarness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The MVP vote after the last level, the awards and complete games, through the engine. */
class VoteAndAwardsTest {

    /** A one-level game whose level ends at once (reversing into the CEO's car). */
    private static GameHarness atTheVote(String... players) {
        GameHarness game = TestGames.start(List.of(TestGames.strip(Map.of())), Map.of(), players);
        press(game, Control.BRAKE, true).advanceSeconds(1.5);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        game.nextTurn();
        return game;
    }

    private static Map<String, Object> voteOf(GameHarness game, String viewer) {
        return TestGames.vote(game.view(viewer));
    }

    // ------------------------------------------------------------------ the vote

    @Test
    void theLastLevelIsFollowedByATwentySecondMvpVote() {
        GameHarness game = atTheVote("ann", "bob", "cleo");

        assertThat(game.round()).isEqualTo(1);
        assertThat(game.turn()).isEqualTo(2);
        assertThat(game.remainingMillis()).isEqualTo(20_000);
        Map<String, Object> view = game.view("ann");
        assertThat(view.get("mode")).isEqualTo("VOTE");
        assertThat(view.get("level")).isNull();
        assertThat((List<?>) view.get("levels")).hasSize(1);
        Map<String, Object> vote = TestGames.vote(view);
        assertThat(vote.get("candidates")).isEqualTo(List.of("ann", "bob", "cleo"));
        assertThat(vote.get("votesCast")).isEqualTo(0);
        assertThat(vote.get("voters")).isEqualTo(3);
        assertThat(vote.get("revealed")).isEqualTo(false);
    }

    @Test
    void nobodyCanVoteForThemselvesAndSpectatorsCantVote() {
        GameHarness game = atTheVote("ann", "bob", "cleo");
        assertThat(game.reject("ann", "vote", Map.of("playerId", "ann")).getMessage())
                .contains("yourself");
        assertThat(game.reject("ann", "vote", Map.of("playerId", "zoe")).getMessage())
                .contains("Pick a player");
        assertThat(game.reject("ann", "vote", Map.of()).getMessage()).contains("playerId");
        assertThat(game.reject("zoe", "vote", Map.of("playerId", "ann")).getMessage())
                .contains("not playing");
        game.act("ann", "input", TestGames.input(Control.GAS, true));
        assertThat(game.phase()).as("stray inputs are ignored").isEqualTo(RunPhase.PLAYING);
    }

    @Test
    void votesStaySecretUntilTheVoteEnds() {
        GameHarness game = atTheVote("ann", "bob", "cleo");
        game.act("ann", "vote", Map.of("playerId", "cleo"));

        Map<String, Object> bob = voteOf(game, "bob");
        assertThat(bob.get("votesCast")).isEqualTo(1);
        assertThat(bob.get("voted")).isEqualTo(List.of("ann"));
        assertThat(bob.get("myVote")).isNull();
        assertThat(bob.get("tally")).isNull();
        assertThat(bob.get("winners")).isNull();
        assertThat(voteOf(game, "ann").get("myVote")).isEqualTo("cleo");
        assertThat(TestGames.vote(game.spectatorView()).get("myVote")).isNull();

        game.act("ann", "vote", Map.of("playerId", "bob"));
        assertThat(voteOf(game, "ann").get("myVote")).as("a vote can be changed").isEqualTo("bob");
        assertThat(voteOf(game, "bob").get("votesCast")).isEqualTo(1);
    }

    @Test
    void theVoteEndsWhenEveryoneVotedAndCrownsTheTeamMvp() {
        GameHarness game = atTheVote("ann", "bob", "cleo");
        game.act("ann", "vote", Map.of("playerId", "bob"));
        game.act("cleo", "vote", Map.of("playerId", "bob"));
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.act("bob", "vote", Map.of("playerId", "ann"));

        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("Team MVP: bob!");
        assertThat(game.lastTurn().points()).isEmpty();
        Map<String, Object> revealed = voteOf(game, "cleo");
        assertThat(revealed.get("revealed")).isEqualTo(true);
        assertThat(revealed.get("tally")).isEqualTo(Map.of("ann", 1, "bob", 2));
        assertThat(revealed.get("winners")).isEqualTo(List.of("bob"));

        game.finishGame();
        assertThat(game.result().awards())
                .extracting(Award::emoji, Award::title, Award::playerId, Award::detail)
                .contains(tuple("🏆", "Team MVP", "bob", "2 votes"));
    }

    @Test
    void aTieCrownsEveryTopPlayer() {
        GameHarness game = atTheVote("ann", "bob", "cleo", "dan");
        game.act("ann", "vote", Map.of("playerId", "bob"))
                .act("bob", "vote", Map.of("playerId", "ann"))
                .act("cleo", "vote", Map.of("playerId", "bob"))
                .act("dan", "vote", Map.of("playerId", "ann"));
        assertThat(game.lastTurn().headline()).isEqualTo("Team MVPs: ann & bob!");
        game.finishGame();
        assertThat(game.result().awards().stream().filter(a -> a.title().equals("Team MVP")))
                .extracting(Award::playerId)
                .containsExactly("ann", "bob");
    }

    @Test
    void theVoteEndsAfterTwentySecondsAndOfflinePlayersDontBlockIt() {
        GameHarness game = atTheVote("ann", "bob", "cleo");
        game.disconnect("cleo");
        game.act("ann", "vote", Map.of("playerId", "cleo"));
        assertThat(voteOf(game, "ann").get("voters")).isEqualTo(2);
        game.advanceSeconds(19.9);
        assertThat(game.phase()).isEqualTo(RunPhase.PLAYING);
        game.advanceSeconds(0.2);
        assertThat(game.phase()).isEqualTo(RunPhase.REVEAL);
        assertThat(game.lastTurn().headline()).isEqualTo("Team MVP: cleo!");

        GameHarness quick = atTheVote("ann", "bob", "cleo");
        quick.disconnect("cleo");
        quick.act("ann", "vote", Map.of("playerId", "bob"))
                .act("bob", "vote", Map.of("playerId", "cleo"));
        assertThat(quick.phase()).as("everyone connected voted").isEqualTo(RunPhase.REVEAL);
    }

    @Test
    void noVotesNoMvp() {
        GameHarness game = atTheVote("ann", "bob", "cleo");
        game.finishTurn();
        assertThat(game.lastTurn().headline()).isEqualTo("No MVP votes — the whole bus wins!");
        game.finishGame();
        assertThat(game.result().awards()).extracting(Award::title).doesNotContain("Team MVP");
    }

    // ------------------------------------------------------------------ awards & full games

    @Test
    void theBestNavigatorTheHornHeroAndTheLeadFootGetAwards() {
        GameHarness game = TestGames.start(TestGames.lanes(Map.of(16, 'G', 40, 'P')));
        String navigator = navigator(game);
        String horn = holder(game, Control.HORN);
        String gas = holder(game, Control.GAS);
        press(game, Control.GAS, true).advanceSeconds(1.6); // just under 6 tiles from the geese
        tap(game, Control.HORN).advanceSeconds(6);
        assertThat(game.phase()).as("parked").isEqualTo(RunPhase.REVEAL);

        game.finishGame();

        assertThat(game.result().awards())
                .extracting(Award::title, Award::playerId)
                .containsExactly(
                        tuple("Best navigator", navigator),
                        tuple("Horn hero", horn),
                        tuple("Lead foot", gas));
        assertThat(game.result().awards().get(1).detail()).isEqualTo("Scattered 1 flock of geese");
    }

    @Test
    void aFullGameOfThreeLevelsEndsOnThePodiumWithTheWholeTeamTied() {
        List<TrackMap> maps =
                List.of(
                        TestGames.strip(Map.of(10, 'P')),
                        TestGames.strip(Map.of()),
                        TestGames.strip(Map.of(8, 'K', 11, 'P')));
        GameHarness game = TestGames.start(maps, Map.of("rounds", 3), "ann", "bob", "cleo");

        press(game, Control.GAS, true).advanceSeconds(3); // level 1: park
        assertThat(game.lastTurn().headline()).startsWith("Parked with 119 s");
        game.nextTurn();
        assertThat(game.round()).isEqualTo(2);
        assertThat(game.turn()).isEqualTo(1);
        press(game, Control.BRAKE, true).advanceSeconds(1.5); // level 2: the CEO's car
        assertThat(game.lastTurn().headline()).startsWith("The CEO's car!");
        game.nextTurn();
        assertThat(game.round()).isEqualTo(3);
        press(game, Control.GAS, true).advanceSeconds(3); // level 3: park with a coffee
        game.nextTurn();
        assertThat(game.turn()).as("the MVP vote").isEqualTo(2);
        assertThat((List<?>) game.view("ann").get("levels")).hasSize(3);
        game.act("ann", "vote", Map.of("playerId", "bob"))
                .act("bob", "vote", Map.of("playerId", "cleo"))
                .act("cleo", "vote", Map.of("playerId", "bob"));
        game.finishGame();

        GameResult result = game.result();
        assertThat(result.history()).hasSize(4);
        assertThat(result.standings()).extracting(s -> s.rank()).containsOnly(1);
        assertThat(game.scores().values()).hasSize(3).containsOnly(game.score("ann"));
        assertThat(game.score("ann")).isGreaterThan(2000);
        assertThat(result.awards()).extracting(Award::title).contains("Team MVP");
    }
}
