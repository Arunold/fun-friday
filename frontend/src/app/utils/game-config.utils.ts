import { 
    Game, 
    GameConfig, 
    NumberGuessConfig, 
    WordScrambleConfig, 
    PictionaryConfig,
    GameRoundInfo,
    NumberGuessRoundInfo,
    WordScrambleRoundInfo,
    PictionaryRoundInfo
} from '../models/game.model';

/**
 * Helper utilities for accessing game config and round info values safely
 */

// ==================== Config Utilities ====================

export function getRoundDuration(game: Game | null | undefined): number {
    return game?.gameConfig?.roundDuration ?? 30;
}

export function getWordLength(game: Game | null | undefined): number {
    const config = game?.gameConfig ?? null;
    return isWordScrambleConfig(config) ? config.wordLength : 7;
}

export function getCurrentTurn(game: Game | null | undefined): number {
    const config = game?.gameConfig ?? null;
    return isPictionaryConfig(config) ? config.currentTurn : 0;
}

export function getTotalTurns(game: Game | null | undefined): number {
    const config = game?.gameConfig ?? null;
    return isPictionaryConfig(config) ? config.totalTurns : 0;
}

export function getMinRange(game: Game | null | undefined): number {
    const config = game?.gameConfig ?? null;
    return isNumberGuessConfig(config) ? config.minRange : 1;
}

export function getMaxRange(game: Game | null | undefined): number {
    const config = game?.gameConfig ?? null;
    return isNumberGuessConfig(config) ? config.maxRange : 100;
}

// ==================== Config Type Guards ====================

export function isPictionaryConfig(config: GameConfig | null): config is PictionaryConfig {
    return config?.type === 'pictionary';
}

export function isWordScrambleConfig(config: GameConfig | null): config is WordScrambleConfig {
    return config?.type === 'word-scramble';
}

export function isNumberGuessConfig(config: GameConfig | null): config is NumberGuessConfig {
    return config?.type === 'number-guess';
}

// ==================== Round Info Type Guards ====================

export function isNumberGuessRoundInfo(info: GameRoundInfo | null | undefined): info is NumberGuessRoundInfo {
    return info?.gameType === 'number-guess';
}

export function isWordScrambleRoundInfo(info: GameRoundInfo | null | undefined): info is WordScrambleRoundInfo {
    return info?.gameType === 'word-scramble';
}

export function isPictionaryRoundInfo(info: GameRoundInfo | null | undefined): info is PictionaryRoundInfo {
    return info?.gameType === 'pictionary';
}
