import { WebPlugin } from '@capacitor/core';

import type {
  Config,
  HttpEvent,
  Location,
  PermissionState,
  State,
  bm_geolocationPlugin,
} from './definitions';

const DEFAULTS: State = {
  desiredAccuracy: 'high',
  distanceFilter: 10,
  locationUpdateInterval: 5000,
  fastestLocationUpdateInterval: 2000,
  maxAccuracy: 100,
  stopOnTerminate: false,
  startOnBoot: true,
  heartbeatInterval: 0,
  url: '',
  method: 'POST',
  headers: {},
  params: {},
  httpRootProperty: 'location',
  autoSync: true,
  batchSync: false,
  maxBatchSize: 100,
  autoSyncThreshold: 0,
  httpTimeout: 15000,
  maxRecordsToPersist: 10000,
  maxDaysToPersist: 3,
  notification: {},
  enabled: false,
};

/**
 * Web fallback (development in the browser only): foreground tracking through
 * navigator.geolocation, no persistence and no upload queue.
 */
export class bm_geolocationWeb extends WebPlugin implements bm_geolocationPlugin {
  private state: State = { ...DEFAULTS };
  private watchId: number | null = null;

  async configure(config: Config): Promise<State> {
    return this.setConfig(config);
  }

  async setConfig(config: Config): Promise<State> {
    this.state = { ...this.state, ...config } as State;
    return this.state;
  }

  async start(): Promise<State> {
    if (this.watchId === null) {
      this.watchId = navigator.geolocation.watchPosition(
        (p) => {
          const loc = this.toLocation(p);
          this.notifyListeners('location', loc);
          void this.upload(loc);
        },
        undefined,
        { enableHighAccuracy: this.state.desiredAccuracy === 'high' },
      );
    }
    this.state.enabled = true;
    this.notifyListeners('enabledchange', { enabled: true });
    return this.state;
  }

  async stop(): Promise<State> {
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
    this.state.enabled = false;
    this.notifyListeners('enabledchange', { enabled: false });
    return this.state;
  }

  async getState(): Promise<State> {
    return this.state;
  }

  getCurrentPosition(options?: { timeout?: number }): Promise<Location> {
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition((p) => resolve({ ...this.toLocation(p), sample: true }), reject, {
        enableHighAccuracy: true,
        timeout: options?.timeout ?? 30000,
      });
    });
  }

  async getLocations(): Promise<{ locations: Location[] }> {
    return { locations: [] };
  }

  async getCount(): Promise<{ count: number }> {
    return { count: 0 };
  }

  async destroyLocations(): Promise<void> {
    return;
  }

  async sync(): Promise<{ count: number; success: boolean }> {
    return { count: 0, success: true };
  }

  async checkPermissions(): Promise<PermissionState> {
    const status = await this.queryGeolocation();
    return { location: status, backgroundLocation: status, notifications: 'granted' };
  }

  async requestPermissions(): Promise<PermissionState> {
    await new Promise<void>((resolve) => navigator.geolocation.getCurrentPosition(() => resolve(), () => resolve()));
    return this.checkPermissions();
  }

  async isIgnoringBatteryOptimizations(): Promise<{ ignoring: boolean; manufacturer: string }> {
    return { ignoring: true, manufacturer: '' };
  }

  async requestIgnoreBatteryOptimizations(): Promise<{ shown: boolean }> {
    return { shown: false };
  }

  async openVendorSettings(): Promise<{ opened: 'vendor' | 'app' }> {
    return { opened: 'app' };
  }

  private async queryGeolocation(): Promise<PermissionState['location']> {
    try {
      const r = await navigator.permissions.query({ name: 'geolocation' });
      return r.state as PermissionState['location'];
    } catch {
      return 'prompt';
    }
  }

  private toLocation(p: GeolocationPosition): Location {
    return {
      uuid: crypto.randomUUID(),
      timestamp: new Date(p.timestamp).toISOString(),
      coords: {
        latitude: p.coords.latitude,
        longitude: p.coords.longitude,
        accuracy: p.coords.accuracy,
        altitude: p.coords.altitude ?? 0,
        speed: p.coords.speed ?? -1,
        heading: p.coords.heading ?? -1,
      },
      battery: { level: -1, is_charging: false },
    };
  }

  private async upload(loc: Location): Promise<void> {
    if (!this.state.url || !this.state.autoSync) return;
    const body = { ...this.state.params, [this.state.httpRootProperty]: loc };
    let event: HttpEvent;
    try {
      const res = await fetch(this.state.url, {
        method: this.state.method,
        headers: { 'Content-Type': 'application/json', ...this.state.headers },
        body: JSON.stringify(body),
      });
      event = { success: res.ok, status: res.status, responseText: await res.text(), count: 1 };
    } catch (e) {
      event = { success: false, status: 0, responseText: String(e), count: 1 };
    }
    this.notifyListeners('http', event);
  }
}
