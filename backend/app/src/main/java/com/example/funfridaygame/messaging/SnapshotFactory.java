package com.example.funfridaygame.messaging;

import com.example.funfridaygame.catalog.GameCatalog;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.AwardView;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.CupEntryView;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.GameRef;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.MemberView;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.Progress;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.Results;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.StandingView;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.TurnView;
import com.example.funfridaygame.messaging.protocol.RoomSnapshot.You;
import com.example.funfridaygame.room.Member;
import com.example.funfridaygame.room.Room;
import com.example.funfridaygame.room.RoomPhase;
import com.example.funfridaygame.runtime.GameResult;
import com.example.funfridaygame.runtime.GameRun;
import com.example.funfridaygame.runtime.TurnRecord;
import com.example.funfridaygame.sdk.GameInfo;
import com.example.funfridaygame.sdk.Viewer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds the personalised {@link RoomSnapshot} for one member. Call while holding the lock. */
@Component
public class SnapshotFactory {

    private final GameCatalog catalog;

    public SnapshotFactory(GameCatalog catalog) {
        this.catalog = catalog;
    }

    /** The snapshot {@code member} receives. */
    public RoomSnapshot build(Room room, Member member, long now) {
        GameRun run = room.run();
        RoomPhase phase = room.phase();
        boolean gameVisible =
                run != null
                        && (phase == RoomPhase.PLAYING
                                || phase == RoomPhase.REVEAL
                                || phase == RoomPhase.FINISHED);
        Map<String, Integer> scores = gameVisible ? run.scores() : Map.of();
        boolean playing = gameVisible && run.isPlaying(member.id());

        Object view = null;
        Long phaseStartedAt = null;
        Long phaseEndsAt = null;
        TurnView lastTurn = null;
        if (phase == RoomPhase.STARTING) {
            phaseStartedAt = room.startingStartedAt();
            phaseEndsAt = room.startingEndsAt();
        } else if (run != null && (phase == RoomPhase.PLAYING || phase == RoomPhase.REVEAL)) {
            phaseStartedAt = run.phaseStartedAt();
            phaseEndsAt = run.phaseEndsAt();
            view = run.view(playing ? Viewer.player(member.id()) : Viewer.spectator());
            if (phase == RoomPhase.REVEAL && run.lastTurn() != null) {
                lastTurn = turnView(run.lastTurn());
            }
        }

        return new RoomSnapshot(
                room.code(),
                phase,
                now,
                phaseStartedAt,
                phaseEndsAt,
                room.hostId(),
                new You(member.id(), member.role(), room.isHost(member), playing),
                room.players().stream().map(m -> memberView(room, m, scores)).toList(),
                room.spectators().stream().map(m -> memberView(room, m, scores)).toList(),
                gameRef(room.gameId()),
                room.settings() == null ? Map.of() : room.settings().asMap(),
                gameVisible
                        ? new Progress(
                                run.round(), run.totalRounds(), run.turn(), run.turnsInRound())
                        : null,
                view,
                lastTurn,
                gameVisible
                        ? run.history().stream().map(SnapshotFactory::turnView).toList()
                        : List.of(),
                phase == RoomPhase.FINISHED && run != null && run.result() != null
                        ? results(run.result())
                        : null,
                room.cupStandings().stream()
                        .map(
                                c ->
                                        new CupEntryView(
                                                c.name(),
                                                c.avatar(),
                                                c.points(),
                                                c.games(),
                                                c.wins()))
                        .toList());
    }

    private GameRef gameRef(String gameId) {
        GameInfo info = catalog.require(gameId).info();
        return new GameRef(
                info.id(), info.name(), info.icon(), info.minPlayers(), info.maxPlayers());
    }

    private static MemberView memberView(Room room, Member m, Map<String, Integer> scores) {
        return new MemberView(
                m.id(),
                m.name(),
                m.avatar(),
                m.color(),
                m.role(),
                m.online(),
                m.ready(),
                room.isHost(m),
                scores.getOrDefault(m.id(), 0));
    }

    private static TurnView turnView(TurnRecord record) {
        return new TurnView(record.round(), record.turn(), record.headline(), record.points());
    }

    private static Results results(GameResult result) {
        Map<String, String> names = new HashMap<>();
        List<StandingView> standings =
                result.standings().stream()
                        .map(
                                s -> {
                                    names.put(s.player().id(), s.player().name());
                                    return new StandingView(
                                            s.player().id(),
                                            s.player().name(),
                                            s.player().avatar(),
                                            s.player().color(),
                                            s.score(),
                                            s.rank());
                                })
                        .toList();
        List<AwardView> awards =
                result.awards().stream()
                        .map(
                                a ->
                                        new AwardView(
                                                a.emoji(),
                                                a.title(),
                                                a.playerId(),
                                                names.getOrDefault(a.playerId(), "?"),
                                                a.detail()))
                        .toList();
        return new Results(standings, awards);
    }
}
