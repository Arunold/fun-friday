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

// Union type for all configs
export type GameConfig = NumberGuessConfig | WordScrambleConfig | PictionaryConfig;

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
    playerGuesses: { [playerName: string]: number };
    targetNumber: number | null;
}

// Word Scramble specific round info
export interface WordScrambleRoundInfo extends BaseRoundInfo {
    gameType: 'word-scramble';
    scrambledWord: string;
    originalWord: string | null;
    playerWordGuesses: { [playerName: string]: string };
}

// Pictionary specific round info
export interface PictionaryRoundInfo extends BaseRoundInfo {
    gameType: 'pictionary';
    drawerName: string;
    wordToDraw: string | null;
    wordHint: string;
    drawingData: any[];
    playerPictionaryGuesses: { [playerName: string]: string[] };
    correctGuesser: string | null;
    currentTurnNumber: number;
    totalTurns: number;
    turnResults: TurnResult[];
}

// Union type for all round info types
export type GameRoundInfo = NumberGuessRoundInfo | WordScrambleRoundInfo | PictionaryRoundInfo;

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
    type: 'DRAWING_STROKE' | 'CLEAR_CANVAS';
    stroke?: DrawingStroke;
}
