package com.example.funfridaygame;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.funfridaygame.engine.RoomQueries;
import com.example.funfridaygame.history.GameRecordRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Functional tests of the real-time protocol: real server, real WebSocket connections, the Number
 * Guess module as the game.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RoomFlowIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private RoomQueries rooms;

    @Autowired private GameRecordRepository records;

    @Test
    void twoPlayersPlayAFullGame() throws Exception {
        try (StompTestClient alice = StompTestClient.connect(port);
                StompTestClient bob = StompTestClient.connect(port)) {
            String code = create(alice, "Alice");

            Map<String, Object> joined =
                    bob.request("join", Map.of("code", code.toLowerCase(), "name", "Bob"));
            assertThat(joined.get("ok")).isEqualTo(true);
            assertThat(data(joined).get("role")).isEqualTo("PLAYER");

            Map<String, Object> lobby = alice.awaitSnapshot(s -> players(s).size() == 2);
            assertThat(lobby.get("phase")).isEqualTo("LOBBY");
            assertThat(map(lobby.get("game")).get("id")).isEqualTo("number-guess");

            Map<String, Object> denied = bob.request("start", Map.of());
            assertThat(denied.get("ok")).isEqualTo(false);
            assertThat(denied.get("error")).as("only the host may start").isEqualTo("NOT_HOST");

            assertThat(alice.request("start", Map.of()).get("ok")).isEqualTo(true);
            alice.awaitPhase("STARTING");
            Map<String, Object> playing = bob.awaitPhase("PLAYING");
            assertThat(map(playing.get("view")).get("target"))
                    .as("the secret stays on the server")
                    .isNull();
            assertThat(playing.get("phaseEndsAt")).isNotNull();

            int target = target(code);
            assertThat(guess(alice, target).get("ok")).isEqualTo(true);
            Map<String, Object> rejected = guess(bob, 0);
            assertThat(rejected.get("error")).isEqualTo("INVALID_ACTION");
            assertThat(guess(bob, target).get("ok")).isEqualTo(true);

            Map<String, Object> reveal = alice.awaitPhase("REVEAL");
            assertThat(map(reveal.get("lastTurn")).get("headline"))
                    .isEqualTo("The number was " + target);
            assertThat(map(reveal.get("view")).get("target")).isEqualTo(target);

            Map<String, Object> finished = bob.awaitPhase("FINISHED");
            List<?> standings = (List<?>) map(finished.get("results")).get("standings");
            assertThat(standings).hasSize(2);
            assertThat((List<?>) finished.get("cup")).hasSize(2);

            long deadline = System.currentTimeMillis() + 5_000;
            while (storedGames(code) == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            assertThat(storedGames(code)).as("finished games are stored").isEqualTo(1);

            assertThat(alice.request("play-again", Map.of()).get("ok")).isEqualTo(true);
            assertThat(bob.awaitPhase("LOBBY").get("results")).isNull();
        }
    }

    @Test
    void aReturningPlayerKeepsTheSeatAndAGonePlayerIsRemovedAfterTheGracePeriod() throws Exception {
        try (StompTestClient alice = StompTestClient.connect(port)) {
            String code = create(alice, "Alice");
            String token;
            try (StompTestClient bob = StompTestClient.connect(port)) {
                token =
                        (String)
                                data(bob.request("join", Map.of("code", code, "name", "Bob")))
                                        .get("token");
            }
            alice.awaitSnapshot(s -> players(s).size() == 2 && !online(s, "Bob"));

            try (StompTestClient bobAgain = StompTestClient.connect(port)) {
                Map<String, Object> resumed =
                        bobAgain.request("resume", Map.of("code", code, "token", token));
                assertThat(resumed.get("ok")).isEqualTo(true);
                alice.awaitSnapshot(s -> online(s, "Bob"));
            }
            alice.awaitSnapshot(s -> players(s).size() == 1);

            try (StompTestClient stranger = StompTestClient.connect(port)) {
                Map<String, Object> expired =
                        stranger.request("resume", Map.of("code", code, "token", token));
                assertThat(expired.get("error")).isEqualTo("SESSION_EXPIRED");
            }
        }
    }

    @Test
    void whenTheHostLeavesTheNextPlayerBecomesHost() throws Exception {
        try (StompTestClient alice = StompTestClient.connect(port);
                StompTestClient bob = StompTestClient.connect(port)) {
            String code = create(alice, "Alice");
            String bobId =
                    (String)
                            data(bob.request("join", Map.of("code", code, "name", "Bob")))
                                    .get("memberId");

            assertThat(alice.request("leave", Map.of()).get("ok")).isEqualTo(true);

            assertThat(bob.awaitEvent("HOST_CHANGED").get("message"))
                    .isEqualTo("Bob is now the host.");
            Map<String, Object> snapshot = bob.awaitSnapshot(s -> bobId.equals(s.get("hostId")));
            assertThat(map(snapshot.get("you")).get("host")).isEqualTo(true);
        }
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        try (StompTestClient alice = StompTestClient.connect(port);
                StompTestClient bob = StompTestClient.connect(port)) {
            assertThat(
                            alice.request("create", Map.of("name", " ", "gameId", "number-guess"))
                                    .get("error"))
                    .isEqualTo("BAD_REQUEST");
            assertThat(
                            alice.request("create", Map.of("name", "Alice", "gameId", "chess"))
                                    .get("error"))
                    .isEqualTo("UNKNOWN_GAME");
            assertThat(
                            alice.request(
                                            "create",
                                            Map.of(
                                                    "name",
                                                    "Alice",
                                                    "gameId",
                                                    "number-guess",
                                                    "settings",
                                                    Map.of("minRange", 100, "maxRange", 50)))
                                    .get("error"))
                    .isEqualTo("INVALID_SETTINGS");
            assertThat(bob.request("join", Map.of("code", "ZZZZZ", "name", "Bob")).get("error"))
                    .isEqualTo("ROOM_NOT_FOUND");

            String code = create(alice, "Alice");
            assertThat(bob.request("join", Map.of("code", code, "name", "alice")).get("error"))
                    .isEqualTo("NAME_TAKEN");
            assertThat(bob.request("join", Map.of("code", code, "name", "Bob:1")).get("error"))
                    .isEqualTo("INVALID_NAME");
            assertThat(alice.request("start", Map.of()).get("error"))
                    .isEqualTo("NOT_ENOUGH_PLAYERS");
            assertThat(
                            bob.request(
                                            "action",
                                            Map.of("type", "guess", "payload", Map.of("value", 1)))
                                    .get("error"))
                    .isEqualTo("NOT_IN_ROOM");
        }
    }

    @Test
    void latecomersWatchAsSpectatorsAndJoinTheNextGame() throws Exception {
        try (StompTestClient alice = StompTestClient.connect(port);
                StompTestClient bob = StompTestClient.connect(port);
                StompTestClient carol = StompTestClient.connect(port)) {
            String code = create(alice, "Alice");
            bob.request("join", Map.of("code", code, "name", "Bob"));
            alice.request("start", Map.of());
            alice.awaitPhase("PLAYING");

            Map<String, Object> joined =
                    carol.request("join", Map.of("code", code, "name", "Carol"));
            assertThat(data(joined).get("role")).isEqualTo("SPECTATOR");
            Map<String, Object> watching = carol.awaitPhase("PLAYING");
            assertThat(map(watching.get("view")).get("myGuesses")).isNull();
            assertThat(guess(carol, 5).get("error")).isEqualTo("INVALID_ACTION");

            int target = target(code);
            guess(alice, target);
            guess(bob, target);
            alice.awaitPhase("FINISHED");
            alice.request("play-again", Map.of());
            Map<String, Object> lobby =
                    carol.awaitSnapshot(
                            s -> "LOBBY".equals(s.get("phase")) && players(s).size() == 3);
            assertThat(map(lobby.get("you")).get("role")).isEqualTo("PLAYER");
        }
    }

    // ------------------------------------------------------------------ helpers

    private String create(StompTestClient client, String name) throws Exception {
        Map<String, Object> reply =
                client.request(
                        "create",
                        Map.of(
                                "name",
                                name,
                                "avatar",
                                "🦊",
                                "gameId",
                                "number-guess",
                                "settings",
                                Map.of("rounds", 1, "roundDuration", 15)));
        assertThat(reply.get("ok")).as(String.valueOf(reply)).isEqualTo(true);
        return (String) data(reply).get("code");
    }

    private Map<String, Object> guess(StompTestClient client, int value) throws Exception {
        return client.request("action", Map.of("type", "guess", "payload", Map.of("value", value)));
    }

    private long storedGames(String code) {
        return records.findAll().stream().filter(r -> code.equals(r.getRoomCode())).count();
    }

    private int target(String code) {
        return (Integer) map(rooms.debugState(code).orElseThrow().get("state")).get("target");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> data(Map<String, Object> reply) {
        return map(reply.get("data"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> players(Map<String, Object> snapshot) {
        return (List<Map<String, Object>>) snapshot.get("players");
    }

    private static boolean online(Map<String, Object> snapshot, String name) {
        return players(snapshot).stream()
                .anyMatch(p -> name.equals(p.get("name")) && Boolean.TRUE.equals(p.get("online")));
    }
}
