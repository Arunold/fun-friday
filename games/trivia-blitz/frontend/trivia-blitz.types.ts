/**
 * The Trivia Blitz view as sent by `TriviaBlitzLogic.view()` (see TriviaBlitzView.java).
 * Fields that are `null` on the server are omitted from the JSON, hence optional here.
 */
export type QuestionDifficulty = 'easy' | 'medium' | 'hard';

export interface PlayerStatus {
  playerId: string;
  /** Whether they have locked in an answer — never which one before the reveal. */
  answered: boolean;
  /** Their run of correct answers, updated at the reveal. */
  streak: number;
  /** Revealed only: whether they were right. */
  correct?: boolean;
  /** Revealed only: how long they took (absent without an answer). */
  answerMillis?: number;
  /** Revealed only: the streak they just lost, when it was 2 or more. */
  lostStreak?: number;
}

export interface Fastest {
  playerId: string;
  millis: number;
}

export interface TriviaBlitzView {
  /** Question number in this game, starting at 1. */
  number: number;
  /** Category label, e.g. "Science". */
  category: string;
  categoryIcon: string;
  difficulty: QuestionDifficulty;
  question: string;
  /** The four answers, in the same order for everyone. */
  options: string[];
  revealed: boolean;
  /** The viewer's locked-in option (absent for spectators and before answering). */
  myAnswer?: number;
  /** The viewer's run of correct answers (absent for spectators). */
  myStreak?: number;
  answeredCount: number;
  players: PlayerStatus[];
  /** Revealed only: index of the correct option. */
  correct?: number;
  /** Revealed only: player ids per option, fastest first. */
  pickers?: string[][];
  /** Revealed only: the fastest correct answer (absent if nobody was right). */
  fastest?: Fastest;
  /** Revealed only: a fun fact about the answer. */
  fact?: string;
}
