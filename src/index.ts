import { registerPlugin } from '@capacitor/core';

import type { bm_geolocationPlugin } from './definitions';

const bm_geolocation = registerPlugin<bm_geolocationPlugin>('bm_geolocation', {
  web: () => import('./web').then((m) => new m.bm_geolocationWeb()),
});

export * from './definitions';
export { bm_geolocation };
