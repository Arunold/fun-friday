import { Player } from './player.model';

// Base game config - all games have roundDuration
export interface BaseGameConfig {
    type: string;
    roundDuration: number;
}

// Number Guess specific config
export interface NumberGuessConfig extends BaseGameConfig {
    type: 'number-guess';
    minRange: number;
    maxRange: number;
}

// Word Scramble specific config
export interface WordScrambleConfig extends BaseGameConfig {
    type: 'word-scramble';
    wordLength: number;
}

// Pictionary specific config
export interface PictionaryConfig extends BaseGameConfig {
    type: 'pictionary';
    currentTurn: number;
    totalTurns: number;
}

// Speed Typing specific config
export interface SpeedTypingConfig extends BaseGameConfig {
    type: 'speed-typing';
    difficulty: 'EASY' | 'MEDIUM' | 'HARD';
    minAccuracy: number;
}

// Reaction Showdown specific config
export interface ReactionShowdownConfig extends BaseGameConfig {
    type: 'reaction-showdown';
    minDelay: number;
    maxDelay: number;
    includeFakeOuts: boolean;
    fakeOutChance: number;
}

// Union type for all configs
export type GameConfig = NumberGuessConfig | WordScrambleConfig
    | PictionaryConfig | SpeedTypingConfig | ReactionShowdownConfig;

export interface Game {
    gameId: string;
    host: string;
    gameTypeId: string;
    gameTypeName: string;
    players: Player[];
    gameState: 'LOBBY' | 'STARTING' | 'GUESSING' | 'DRAWING' | 'ROUND_RESULT' | 'FINISHED';
    totalRounds: number;
    currentRoundNumber: number;
    currentRoundInfo: GameRoundInfo | null;
    gameConfig: GameConfig | null;
}

export interface TurnResult {
    turnNumber: number;
    drawerName: string;
    wordToDraw: string;
    correctGuesser: string | null;
    drawerPoints: number;
    guesserPoints: number;
}

// Base round info - common fields for all games
export interface BaseRoundInfo {
    gameType: string;
    roundNumber: number;
    winner: string | null;
    revealed: boolean;
}

// Number Guess specific round info
export interface NumberGuessRoundInfo extends BaseRoundInfo {
    gameType: 'number-guess';
    minRange: number;
    maxRange: number;
    playerGuesses: Record<string, number>;
    targetNumber: number | null;
}

// Word Scramble specific round info
export interface WordScrambleRoundInfo extends BaseRoundInfo {
    gameType: 'word-scramble';
    scrambledWord: string;
    originalWord: string | null;
    playerWordGuesses: Record<string, string>;
}

// Pictionary specific round info
export interface PictionaryRoundInfo extends BaseRoundInfo {
    gameType: 'pictionary';
    drawerName: string;
    wordToDraw: string | null;
    wordHint: string;
    drawingData: DrawingStroke[];
    playerPictionaryGuesses: Record<string, string[]>;
    correctGuesser: string | null;
    currentTurnNumber: number;
    totalTurns: number;
    turnResults: TurnResult[];
}

// Speed Typing player progress info
export interface PlayerProgressInfo {
    typedText: string;
    percentage: number;
    accuracy: number;
    finished: boolean;
    finishTime: number;
}

// Speed Typing specific round info
export interface SpeedTypingRoundInfo extends BaseRoundInfo {
    gameType: 'speed-typing';
    targetText: string;
    difficulty: 'EASY' | 'MEDIUM' | 'HARD';
    minAccuracy: number;
    startTime: number;
    playerProgress: Record<string, PlayerProgressInfo>;
    rankings: string[];
}

// Reaction Showdown player reaction info
export interface ReactionInfo {
    reactionTime: number;
    falseStart: boolean;
    tappedFakeOut: boolean;
}

// Reaction Showdown specific round info
export interface ReactionShowdownRoundInfo extends BaseRoundInfo {
    gameType: 'reaction-showdown';
    phase: 'WAITING' | 'REACT' | 'FAKEOUT';
    isFakeOut: boolean;
    signalTime: number;
    playerReactions: Record<string, ReactionInfo>;
    fastestPlayer: string | null;
    fastestTime: number;
}

// Union type for all round info types
export type GameRoundInfo = NumberGuessRoundInfo | WordScrambleRoundInfo
    | PictionaryRoundInfo | SpeedTypingRoundInfo | ReactionShowdownRoundInfo;

export interface GameType {
    id: string;
    name: string;
    description: string;
    icon: string;
    minPlayers: number;
    maxPlayers: number;
}

// Drawing stroke for Pictionary
export interface DrawingStroke {
    points: { x: number; y: number }[];
    color: string;
    size: number;
}

// Pictionary guess message from WebSocket
export interface PictionaryGuessMessage {
    type: 'PICTIONARY_GUESS';
    playerName: string;
    guess: string;
}

// Drawing message from WebSocket
export interface DrawingMessage {
    type: 'DRAWING_STROKE' | 'CLEAR_CANVAS' | 'UNDO_STROKE' | 'REDO_STROKE';
    stroke?: DrawingStroke;
}
