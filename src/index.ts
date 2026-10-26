import { registerPlugin } from '@capacitor/core';

import type { SwbUpdaterPlugin } from './definitions';

const SwbUpdater = registerPlugin<SwbUpdaterPlugin>('SwbUpdater', {
  web: () => import('./web').then(m => new m.SwbUpdaterWeb()),
});

export * from './definitions';
export { SwbUpdater };
