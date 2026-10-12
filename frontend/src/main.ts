import { bootstrapApplication } from '@angular/platform-browser';

import { App } from './app/app';
import { appConfig } from './app/app.config';

/**
 * Invite links of the first version looked like `/ui/#/setup/join/ABCDE` (the server redirects
 * `/ui/` to `/` and the browser keeps the fragment). Translate them to the new path-based routes.
 */
function upgradeLegacyLink(): void {
  const legacy = /^#\/setup\/join\/([A-Za-z0-9]+)/.exec(window.location.hash);
  if (legacy) {
    window.history.replaceState(null, '', `/join/${legacy[1].toUpperCase()}`);
  } else if (window.location.hash.startsWith('#/')) {
    window.history.replaceState(null, '', '/');
  }
}

upgradeLegacyLink();
bootstrapApplication(App, appConfig).catch((error: unknown) => console.error(error));
