package com.example.funfridaygame.history;

import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

/** Stored games. */
public interface GameRecordRepository extends ListCrudRepository<GameRecord, Long> {

    /** The latest games of one room instance. */
    List<GameRecord> findTop20ByRoomIdOrderByFinishedAtDesc(String roomId);

    /** The latest games overall. */
    List<GameRecord> findTop20ByOrderByFinishedAtDesc();
}
