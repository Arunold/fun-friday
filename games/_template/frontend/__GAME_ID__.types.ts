/** Mirror of __CLASS__View.java (null fields are omitted from the JSON, hence optional). */
export interface PlayerPick {
  playerId: string;
  picked: boolean;
  /** Revealed only. */
  value?: number;
}

export interface __CLASS__View {
  highest: number;
  revealed: boolean;
  /** Revealed only. */
  lucky?: number;
  /** Absent for spectators and before picking. */
  myPick?: number;
  players: PlayerPick[];
}
