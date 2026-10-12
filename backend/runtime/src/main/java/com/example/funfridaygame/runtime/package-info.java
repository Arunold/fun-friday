/**
 * Game runtime: drives one {@link com.example.funfridaygame.sdk.GameLogic} through its rounds and
 * turns — deadlines, game timers, reveal pauses, scoring, standings and awards.
 *
 * <p>It is plain Java without Spring or I/O so the same engine runs in production (the {@code app}
 * module wraps it with real timers, locking and WebSocket broadcasting) and in scenario tests (the
 * {@code testkit} module uses a virtual clock). A {@link com.example.funfridaygame.runtime.GameRun}
 * is not thread-safe: its owner must serialize every call, including timer callbacks.
 */
package com.example.funfridaygame.runtime;
