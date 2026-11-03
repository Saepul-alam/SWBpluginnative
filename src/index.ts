import { registerPlugin } from '@capacitor/core';

import type { SwbNotifPlugin } from './definitions';

const SwbNotif = registerPlugin<SwbNotifPlugin>('SwbNotif', {
  web: () => import('./web').then(m => new m.SwbNotifWeb()),
});

export * from './definitions';
export * from './validation';
export { SwbNotif };
