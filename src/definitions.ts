import type { PluginListenerHandle } from '@capacitor/core';

export type PermissionStatus = 'prompt' | 'prompt-with-rationale' | 'granted' | 'denied';

export type DesiredAccuracy = 'high' | 'balanced' | 'low';

export interface NotificationConfig {
  /** Title of the foreground-service notification (Android). @default "Tracking location" */
  title?: string;
  /** Text of the foreground-service notification (Android). @default "Location tracking is active" */
  text?: string;
  /** Name of the notification channel (Android). @default "Location tracking" */
  channelName?: string;
  /** Name of a drawable/mipmap resource used as the small icon (Android). */
  smallIcon?: string;
}

export interface Config {
  /** Location accuracy. @default "high" */
  desiredAccuracy?: DesiredAccuracy;
  /** Minimum distance (meters) the device must move before a new location is recorded. @default 10 */
  distanceFilter?: number;
  /** Desired interval between location updates, in ms (Android). @default 5000 */
  locationUpdateInterval?: number;
  /** Fastest accepted interval between location updates, in ms (Android). @default 2000 */
  fastestLocationUpdateInterval?: number;
  /** Drop fixes whose horizontal accuracy (meters) is worse than this value. 0 disables. @default 100 */
  maxAccuracy?: number;
  /** Stop tracking when the app is swiped away / terminated. @default false */
  stopOnTerminate?: boolean;
  /** Resume tracking after device reboot (Android). @default true */
  startOnBoot?: boolean;
  /**
   * Seconds. If > 0 and no location was recorded for this long (e.g. the courier stands still and distanceFilter
   * produces no updates), a point flagged `heartbeat: true` is recorded and uploaded. 0 disables. @default 0
   */
  heartbeatInterval?: number;

  /** Server endpoint that receives the locations. If empty, locations are only stored locally. */
  url?: string;
  /** HTTP method. @default "POST" */
  method?: 'POST' | 'PUT';
  /** Extra HTTP headers (e.g. Authorization). */
  headers?: Record<string, string>;
  /** Extra properties merged into the root of every request body (e.g. courier_id, order_id). */
  params?: Record<string, unknown>;
  /** Name of the root property holding the location(s). @default "location" */
  httpRootProperty?: string;
  /** Upload locations automatically as they are recorded. @default true */
  autoSync?: boolean;
  /** Send several locations in one request (array under httpRootProperty). @default false */
  batchSync?: boolean;
  /** Maximum number of locations per batch request. @default 100 */
  maxBatchSize?: number;
  /** Wait until this many locations are stored before auto-syncing. @default 0 */
  autoSyncThreshold?: number;
  /** HTTP timeout in ms. @default 15000 */
  httpTimeout?: number;

  /** Maximum number of locations kept in the local database. @default 10000 */
  maxRecordsToPersist?: number;
  /** Maximum age in days of locations kept in the local database. @default 3 */
  maxDaysToPersist?: number;

  /** Foreground service notification (Android). */
  notification?: NotificationConfig;
}

export interface Coords {
  latitude: number;
  longitude: number;
  /** Horizontal accuracy in meters. */
  accuracy: number;
  /** Altitude in meters. */
  altitude: number;
  /** Speed in m/s (-1 if unknown). */
  speed: number;
  /** Heading in degrees (-1 if unknown). */
  heading: number;
}

export interface Location {
  uuid: string;
  /** ISO-8601 timestamp of the fix. */
  timestamp: string;
  coords: Coords;
  battery: { is_charging: boolean; level: number };
  /** Compatible with Transistorsoft / DeliveryApp SendGPS. */
  is_moving?: boolean;
  odometer?: number;
  activity?: { type: string; confidence: number };
  extras?: Record<string, unknown>;
  /** True for locations produced by getCurrentPosition(). */
  sample?: boolean;
  /** True for points recorded by the heartbeat or by a push wake-up. */
  heartbeat?: boolean;
}

export interface State extends Required<Omit<Config, 'notification' | 'headers' | 'params' | 'url'>> {
  url: string;
  headers: Record<string, string>;
  params: Record<string, unknown>;
  notification: NotificationConfig;
  /** Whether tracking is currently enabled. */
  enabled: boolean;
  /** Epoch ms of the last recorded location (0 = none). Use it as a watchdog: if tracking is enabled but this is old, the OS probably killed/throttled the app. */
  lastLocationAt?: number;
}

export interface HttpEvent {
  success: boolean;
  /** HTTP status code (0 on network failure). */
  status: number;
  responseText: string;
  /** Number of locations in the request. */
  count: number;
}

export interface PermissionState {
  /** Foreground (while-in-use) location. */
  location: PermissionStatus;
  /** Background ("always") location. */
  backgroundLocation: PermissionStatus;
  /** Notifications (needed on Android 13+ for the foreground-service notification). */
  notifications: PermissionStatus;
}

export interface bm_geolocationPlugin {
  /**
   * Applies configuration (merged over defaults and previously stored config) and returns the state.
   * Call once on app start, before start().
   */
  configure(config: Config): Promise<State>;
  /** Changes configuration at runtime (e.g. new auth token, new order id in params). */
  setConfig(config: Config): Promise<State>;
  /** Starts background tracking. Requires location permission. */
  start(): Promise<State>;
  /** Stops background tracking. */
  stop(): Promise<State>;
  getState(): Promise<State>;

  /** One-shot high accuracy fix. Not persisted. */
  getCurrentPosition(options?: { timeout?: number }): Promise<Location>;

  /** Locations stored locally and not uploaded yet. */
  getLocations(options?: { limit?: number }): Promise<{ locations: Location[] }>;
  getCount(): Promise<{ count: number }>;
  destroyLocations(): Promise<void>;
  /** Uploads stored locations immediately. */
  sync(): Promise<{ count: number; success: boolean }>;

  checkPermissions(): Promise<PermissionState>;
  /**
   * Requests permissions. With `background: true` it asks for "always" access after foreground access
   * has been granted (required for tracking while the app is in background).
   */
  requestPermissions(options?: { background?: boolean }): Promise<PermissionState>;

  /** Android: whether the app is exempt from battery optimization. Always true on iOS/web. */
  isIgnoringBatteryOptimizations(): Promise<{ ignoring: boolean; manufacturer: string }>;
  /** Android: shows the system dialog to exempt the app from battery optimization. */
  requestIgnoreBatteryOptimizations(): Promise<{ shown: boolean }>;
  /**
   * Android: opens the manufacturer's autostart / background screen (Xiaomi, Huawei/Honor, Samsung, Oppo/Realme, Vivo),
   * or the app details screen if unavailable. iOS: opens the app settings.
   */
  openVendorSettings(): Promise<{ opened: 'vendor' | 'app' }>;

  addListener(eventName: 'location', listener: (location: Location) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'http', listener: (event: HttpEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'enabledchange', listener: (event: { enabled: boolean }) => void): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}
