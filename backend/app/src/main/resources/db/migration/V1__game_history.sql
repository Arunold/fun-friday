CREATE TABLE game_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id       VARCHAR(36)              NOT NULL,
    room_code     VARCHAR(8)               NOT NULL,
    game_id       VARCHAR(40)              NOT NULL,
    game_name     VARCHAR(80)              NOT NULL,
    rounds        INT                      NOT NULL,
    settings_json VARCHAR(4000)            NOT NULL,
    started_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_game_record_room ON game_record (room_id, finished_at);
CREATE INDEX idx_game_record_finished ON game_record (finished_at);

CREATE TABLE game_record_player (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    game_record_id BIGINT      NOT NULL,
    player_name    VARCHAR(40) NOT NULL,
    avatar         VARCHAR(32),
    score          INT         NOT NULL,
    placement      INT         NOT NULL,
    cup_points     INT         NOT NULL,
    CONSTRAINT fk_player_game_record FOREIGN KEY (game_record_id)
        REFERENCES game_record (id) ON DELETE CASCADE
);

CREATE INDEX idx_game_record_player_record ON game_record_player (game_record_id);
