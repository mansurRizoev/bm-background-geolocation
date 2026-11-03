import { WebPlugin } from '@capacitor/core';

import type { bm_geolocationPlugin } from './definitions';

export class bm_geolocationWeb extends WebPlugin implements bm_geolocationPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
