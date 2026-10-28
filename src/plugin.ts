import { registerPlugin } from '@capacitor/core';

import type { SwbPlugin as SwbPluginInterface } from './definitions';

export const SwbPlugin = registerPlugin<SwbPluginInterface>('SwbPlugin', {
  web: () => import('./web').then(m => new m.SwbPluginWeb()),
});

