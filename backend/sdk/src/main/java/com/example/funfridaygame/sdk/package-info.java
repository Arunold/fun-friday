/**
 * Game SDK — the only API a game module needs.
 *
 * <p>A game is a folder {@code games/<id>/} with a {@code backend/} and a {@code frontend/} part.
 * The backend part implements two types:
 *
 * <ul>
 *   <li>{@link com.example.funfridaygame.sdk.GameDefinition} — metadata ({@link
 *       com.example.funfridaygame.sdk.GameInfo}), the settings schema ({@link
 *       com.example.funfridaygame.sdk.SettingDef}) and a factory for the per-game logic. Annotate
 *       it with {@link com.example.funfridaygame.sdk.FunFridayGame} so the platform discovers it.
 *   <li>{@link com.example.funfridaygame.sdk.GameLogic} — the rules for one game run: start a turn,
 *       react to player actions and timers, decide when a turn is complete, score it and render a
 *       per-viewer view.
 * </ul>
 *
 * <p>The platform owns everything else: rooms, players, presence, the lobby, timers, the round and
 * turn loop, scoreboards, results, persistence and the network protocol. All callbacks on a {@code
 * GameLogic} are serialized per room, so game code never needs locks.
 */
package com.example.funfridaygame.sdk;
