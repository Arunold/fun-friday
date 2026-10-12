package com.example.funfridaygame.messaging.protocol;

import com.example.funfridaygame.room.Member;
import com.example.funfridaygame.room.RoomPhase;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The complete, personalised state of a room, sent to each member on {@code /user/queue/room} after
 * every change. Clients simply replace their copy.
 *
 * @param code room code
 * @param phase room phase
 * @param serverTime server clock when the snapshot was built (epoch ms), for clock sync
 * @param phaseStartedAt when the current timed phase began (epoch ms), for progress rings
 * @param phaseEndsAt when the countdown, turn or reveal ends (epoch ms), if timed
 * @param hostId member id of the host
 * @param you who this snapshot is for
 * @param players players in join order
 * @param spectators spectators in join order
 * @param game the selected game
 * @param settings the selected settings
 * @param progress round/turn counters while a game runs
 * @param view the game's view for you (PLAYING and REVEAL)
 * @param lastTurn the turn shown on the reveal screen
 * @param history every finished turn of the current or last game
 * @param results podium data (FINISHED)
 * @param cup the room's Friday Cup across games
 */
public record RoomSnapshot(
        String code,
        RoomPhase phase,
        long serverTime,
        @Nullable Long phaseStartedAt,
        @Nullable Long phaseEndsAt,
        String hostId,
        You you,
        List<MemberView> players,
        List<MemberView> spectators,
        GameRef game,
        Map<String, Object> settings,
        @Nullable Progress progress,
        @Nullable Object view,
        @Nullable TurnView lastTurn,
        List<TurnView> history,
        @Nullable Results results,
        List<CupEntryView> cup) {

    /**
     * The receiving member.
     *
     * @param memberId member id
     * @param role PLAYER or SPECTATOR
     * @param host whether you are the host
     * @param playing whether you take part in the running game
     */
    public record You(String memberId, Member.Role role, boolean host, boolean playing) {}

    /**
     * A member.
     *
     * @param id member id
     * @param name display name
     * @param avatar emoji
     * @param color CSS colour
     * @param role PLAYER or SPECTATOR
     * @param online connected right now
     * @param ready ready in the lobby
     * @param host is the host
     * @param score score in the current or last game
     */
    public record MemberView(
            String id,
            String name,
            String avatar,
            String color,
            Member.Role role,
            boolean online,
            boolean ready,
            boolean host,
            int score) {}

    /**
     * The selected game.
     *
     * @param id game id
     * @param name display name
     * @param icon emoji
     * @param minPlayers players needed to start
     * @param maxPlayers player limit
     */
    public record GameRef(String id, String name, String icon, int minPlayers, int maxPlayers) {}

    /**
     * Game progress.
     *
     * @param round current round
     * @param totalRounds rounds in the game
     * @param turn current turn in the round
     * @param turnsInRound turns in the round
     */
    public record Progress(int round, int totalRounds, int turn, int turnsInRound) {}

    /**
     * A finished turn.
     *
     * @param round round number
     * @param turn turn number
     * @param headline reveal headline
     * @param points points per member id
     */
    public record TurnView(int round, int turn, String headline, Map<String, Integer> points) {}

    /**
     * Podium data.
     *
     * @param standings final ranking (ties share a rank)
     * @param awards fun titles
     */
    public record Results(List<StandingView> standings, List<AwardView> awards) {}

    /**
     * One line of the ranking.
     *
     * @param playerId member id
     * @param name display name
     * @param avatar emoji
     * @param color CSS colour
     * @param score final score
     * @param rank 1-based rank
     */
    public record StandingView(
            String playerId, String name, String avatar, String color, int score, int rank) {}

    /**
     * An award.
     *
     * @param emoji decoration
     * @param title title
     * @param playerId member id
     * @param playerName display name
     * @param detail e.g. "182 ms"
     */
    public record AwardView(
            String emoji,
            String title,
            String playerId,
            String playerName,
            @Nullable String detail) {}

    /**
     * One line of the Friday Cup.
     *
     * @param name display name
     * @param avatar emoji
     * @param points cup points (10/6/4 for the podium, 2 for taking part)
     * @param games games played
     * @param wins games won
     */
    public record CupEntryView(String name, String avatar, int points, int games, int wins) {}
}
