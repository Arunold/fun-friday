export interface Player {
    name: string;
    score: number;
    avatar?: string;  // emoji avatar
}

export interface Avatar {
    id: string;
    emoji: string;
    label: string;
}
