import { Injectable, signal } from '@angular/core';

import { readStorage, writeStorage } from './util';

/** How a person appears in rooms. Remembered in this browser; there are no accounts. */
export interface Profile {
  name: string;
  avatar: string;
}

const KEY = 'ff.profile';
/** First-visit avatars, so new players don't all look the same. */
const STARTER_AVATARS = [
  '🦊',
  '🐼',
  '🐨',
  '🐯',
  '🦁',
  '🐸',
  '🐵',
  '🐧',
  '🦉',
  '🐙',
  '🦄',
  '🐝',
  '🐢',
  '🦖',
  '🐳',
  '🦩',
];

/** Mirrors the server's NameRules: letters, digits, spaces and . _ ' -, at most 20 characters. */
export const NAME_PATTERN = /^[\p{L}\p{N} _.'-]{1,20}$/u;
export const NAME_MAX_LENGTH = 20;

@Injectable({ providedIn: 'root' })
export class ProfileService {
  readonly profile = signal<Profile>(
    readStorage<Profile>(globalThis.localStorage, KEY) ?? {
      name: '',
      avatar: STARTER_AVATARS[Math.floor(Math.random() * STARTER_AVATARS.length)],
    },
  );

  save(profile: Profile): void {
    const clean = { name: profile.name.trim().replace(/\s+/g, ' '), avatar: profile.avatar };
    this.profile.set(clean);
    writeStorage(globalThis.localStorage, KEY, clean);
  }
}
