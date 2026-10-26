import { WebPlugin } from '@capacitor/core';

import type { SwbUpdaterPlugin } from './definitions';

export class SwbUpdaterWeb extends WebPlugin implements SwbUpdaterPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
