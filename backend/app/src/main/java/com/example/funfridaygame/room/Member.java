package com.example.funfridaygame.room;

import com.example.funfridaygame.sdk.PlayerInfo;

/** Someone in a room: a player or a spectator. Guarded by the room lock. */
public final class Member {

    /** Whether the member plays or watches. */
    public enum Role {
        PLAYER,
        SPECTATOR
    }

    private final String id;
    private final String token;
    private final long joinedAt;
    private final RateLimiter limiter;
    private String name;
    private String avatar;
    private String color;
    private Role role;
    private boolean wantsToPlay;
    private String principal;
    private boolean online;
    private boolean ready;
    private long presenceEpoch;

    Member(
            String id,
            String token,
            String name,
            String avatar,
            String color,
            Role role,
            long now,
            RateLimiter limiter) {
        this.id = id;
        this.token = token;
        this.name = name;
        this.avatar = avatar;
        this.color = color;
        this.role = role;
        this.joinedAt = now;
        this.limiter = limiter;
        this.wantsToPlay = role == Role.PLAYER;
    }

    /** The view of this member that game modules get. */
    public PlayerInfo toPlayerInfo() {
        return new PlayerInfo(id, name, avatar, color);
    }

    public String id() {
        return id;
    }

    /** Secret used to resume the seat after a reconnect. */
    public String token() {
        return token;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public RateLimiter limiter() {
        return limiter;
    }

    public String name() {
        return name;
    }

    public String avatar() {
        return avatar;
    }

    public String color() {
        return color;
    }

    public Role role() {
        return role;
    }

    public boolean isPlayer() {
        return role == Role.PLAYER;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    /** Whether a spectator should become a player when the room is back in the lobby. */
    public boolean wantsToPlay() {
        return wantsToPlay;
    }

    public void setWantsToPlay(boolean wantsToPlay) {
        this.wantsToPlay = wantsToPlay;
    }

    /** Name of the WebSocket connection's principal, {@code null} when disconnected. */
    public String principal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public boolean online() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public boolean ready() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    /** Incremented on every (dis)connect so stale grace timers can be ignored. */
    public long presenceEpoch() {
        return presenceEpoch;
    }

    public long nextPresenceEpoch() {
        return ++presenceEpoch;
    }
}
