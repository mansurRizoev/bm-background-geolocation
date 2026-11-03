# bm-background-geolocation

Capacitor plugin for background geolocation and real-time GPS tracking (courier apps). Inspired by `cordova-background-geolocation-lt`.

- Android: foreground service + Fused Location Provider, survives app swipe-away and device reboot.
- iOS: CoreLocation with background mode + significant-change relaunch.
- Locations are stored in a native SQLite queue and uploaded **natively** (JSON over HTTP), so tracking and sync work even when the WebView is not alive. Rows are removed only after a 2xx response, so nothing is lost offline.

## Features

- Background location tracking
- GPS location updates
- Android foreground service support

## License

MIT

## Install

```bash
npm install bm-background-geolocation
npx cap sync
```

## Usage

```ts
import { bm_geolocation } from 'bm-background-geolocation';

await bm_geolocation.configure({
  desiredAccuracy: 'high',
  distanceFilter: 20,
  url: 'https://api.example.com/couriers/location',
  headers: { Authorization: `Bearer ${token}` },
  params: { courier_id: 42 },        // merged into every request body
  batchSync: true,
  maxBatchSize: 50,
  notification: { title: 'Shift in progress', text: 'Sharing your location' },
});

const perm = await bm_geolocation.requestPermissions({ background: true });
if (perm.location === 'granted') await bm_geolocation.start();

bm_geolocation.addListener('location', (l) => console.log(l.coords));
bm_geolocation.addListener('http', (e) => console.log(e.status, e.count));

// end of shift
await bm_geolocation.stop();
```

Request body (`httpRootProperty` = `location`):

```json
{ "courier_id": 42, "location": { "uuid": "…", "timestamp": "2026-01-01T10:00:00.000Z",
  "coords": { "latitude": 0, "longitude": 0, "accuracy": 5, "altitude": 0, "speed": 1.2, "heading": 90 },
  "battery": { "level": 0.8, "is_charging": false } } }
```
With `batchSync: true` the value is an array of such objects. Change the token/order at runtime with `setConfig({ headers, params })`.

## Android setup

Manifest permissions, the foreground service and the boot receiver are merged automatically. Requirements:

- Ask for background location **after** foreground location (`requestPermissions({ background: true })` does this in sequence; Android 11+ sends the user to settings for "Allow all the time").
- Android 13+ needs the notifications permission for the foreground-service notification (requested by `requestPermissions`).
- Google Play requires a declaration and in-app disclosure for background location. Some OEMs (Xiaomi, Huawei, Samsung…) kill background apps: ask couriers to exclude the app from battery optimization.

### Xiaomi / Huawei / Samsung and battery optimization

OEM firmwares kill background apps aggressively. Walk the courier through this once (onboarding):

```ts
const { ignoring, manufacturer } = await bm_geolocation.isIgnoringBatteryOptimizations();
if (!ignoring) await bm_geolocation.requestIgnoreBatteryOptimizations(); // system dialog
// then show instructions and open the OEM autostart / background screen:
await bm_geolocation.openVendorSettings(); // Xiaomi, Huawei/Honor, Samsung, Oppo/Realme, Vivo; falls back to app details
```

Manual steps: Xiaomi - Autostart on, Battery saver "No restrictions", pin app in recents. Huawei - App launch: manual, allow autostart / secondary launch / run in background. Samsung - Battery: remove the app from "Sleeping/Deep sleeping apps", disable "Put unused apps to sleep".

Watchdog: `getState()` returns `lastLocationAt` (epoch ms). If `enabled` is true but it is much older than your interval, the OS likely throttled the app - show the courier a hint and/or send a high-priority push from the server.

## iOS setup

Add to `Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key><string>Needed to track deliveries</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key><string>Needed to track deliveries in background</string>
<key>UIBackgroundModes</key><array><string>location</string></array>
```

Without `UIBackgroundModes → location`, background updates are disabled. After termination iOS relaunches the app on significant location changes (`stopOnTerminate: false`).

## API

See `src/definitions.ts` (documented types): `configure`, `setConfig`, `start`, `stop`, `getState`, `getCurrentPosition`, `getLocations`, `getCount`, `destroyLocations`, `sync`, `checkPermissions`, `requestPermissions`, and events `location`, `http`, `enabledchange`.

<docgen-index>

* [`configure(...)`](#configure)
* [`setConfig(...)`](#setconfig)
* [`start()`](#start)
* [`stop()`](#stop)
* [`getState()`](#getstate)
* [`getCurrentPosition(...)`](#getcurrentposition)
* [`getLocations(...)`](#getlocations)
* [`getCount()`](#getcount)
* [`destroyLocations()`](#destroylocations)
* [`sync()`](#sync)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions(...)`](#requestpermissions)
* [`isIgnoringBatteryOptimizations()`](#isignoringbatteryoptimizations)
* [`requestIgnoreBatteryOptimizations()`](#requestignorebatteryoptimizations)
* [`openVendorSettings()`](#openvendorsettings)
* [`addListener('location', ...)`](#addlistenerlocation-)
* [`addListener('http', ...)`](#addlistenerhttp-)
* [`addListener('enabledchange', ...)`](#addlistenerenabledchange-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### configure(...)

```typescript
configure(config: Config) => Promise<State>
```

Applies configuration (merged over defaults and previously stored config) and returns the state.
Call once on app start, before start().

| Param        | Type                                      |
| ------------ | ----------------------------------------- |
| **`config`** | <code><a href="#config">Config</a></code> |

**Returns:** <code>Promise&lt;<a href="#state">State</a>&gt;</code>

--------------------


### setConfig(...)

```typescript
setConfig(config: Config) => Promise<State>
```

Changes configuration at runtime (e.g. new auth token, new order id in params).

| Param        | Type                                      |
| ------------ | ----------------------------------------- |
| **`config`** | <code><a href="#config">Config</a></code> |

**Returns:** <code>Promise&lt;<a href="#state">State</a>&gt;</code>

--------------------


### start()

```typescript
start() => Promise<State>
```

Starts background tracking. Requires location permission.

**Returns:** <code>Promise&lt;<a href="#state">State</a>&gt;</code>

--------------------


### stop()

```typescript
stop() => Promise<State>
```

Stops background tracking.

**Returns:** <code>Promise&lt;<a href="#state">State</a>&gt;</code>

--------------------


### getState()

```typescript
getState() => Promise<State>
```

**Returns:** <code>Promise&lt;<a href="#state">State</a>&gt;</code>

--------------------


### getCurrentPosition(...)

```typescript
getCurrentPosition(options?: { timeout?: number | undefined; } | undefined) => Promise<Location>
```

One-shot high accuracy fix. Not persisted.

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ timeout?: number; }</code> |

**Returns:** <code>Promise&lt;<a href="#location">Location</a>&gt;</code>

--------------------


### getLocations(...)

```typescript
getLocations(options?: { limit?: number | undefined; } | undefined) => Promise<{ locations: Location[]; }>
```

Locations stored locally and not uploaded yet.

| Param         | Type                             |
| ------------- | -------------------------------- |
| **`options`** | <code>{ limit?: number; }</code> |

**Returns:** <code>Promise&lt;{ locations: Location[]; }&gt;</code>

--------------------


### getCount()

```typescript
getCount() => Promise<{ count: number; }>
```

**Returns:** <code>Promise&lt;{ count: number; }&gt;</code>

--------------------


### destroyLocations()

```typescript
destroyLocations() => Promise<void>
```

--------------------


### sync()

```typescript
sync() => Promise<{ count: number; success: boolean; }>
```

Uploads stored locations immediately.

**Returns:** <code>Promise&lt;{ count: number; success: boolean; }&gt;</code>

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionState>
```

**Returns:** <code>Promise&lt;<a href="#permissionstate">PermissionState</a>&gt;</code>

--------------------


### requestPermissions(...)

```typescript
requestPermissions(options?: { background?: boolean | undefined; } | undefined) => Promise<PermissionState>
```

Requests permissions. With `background: true` it asks for "always" access after foreground access
has been granted (required for tracking while the app is in background).

| Param         | Type                                   |
| ------------- | -------------------------------------- |
| **`options`** | <code>{ background?: boolean; }</code> |

**Returns:** <code>Promise&lt;<a href="#permissionstate">PermissionState</a>&gt;</code>

--------------------


### isIgnoringBatteryOptimizations()

```typescript
isIgnoringBatteryOptimizations() => Promise<{ ignoring: boolean; manufacturer: string; }>
```

Android: whether the app is exempt from battery optimization. Always true on iOS/web.

**Returns:** <code>Promise&lt;{ ignoring: boolean; manufacturer: string; }&gt;</code>

--------------------


### requestIgnoreBatteryOptimizations()

```typescript
requestIgnoreBatteryOptimizations() => Promise<{ shown: boolean; }>
```

Android: shows the system dialog to exempt the app from battery optimization.

**Returns:** <code>Promise&lt;{ shown: boolean; }&gt;</code>

--------------------


### openVendorSettings()

```typescript
openVendorSettings() => Promise<{ opened: 'vendor' | 'app'; }>
```

Android: opens the manufacturer's autostart / background screen (Xiaomi, Huawei/Honor, Samsung, Oppo/Realme, Vivo),
or the app details screen if unavailable. iOS: opens the app settings.

**Returns:** <code>Promise&lt;{ opened: 'vendor' | 'app'; }&gt;</code>

--------------------


### addListener('location', ...)

```typescript
addListener(eventName: 'location', listener: (location: Location) => void) => Promise<PluginListenerHandle>
```

| Param           | Type                                                                 |
| --------------- | -------------------------------------------------------------------- |
| **`eventName`** | <code>'location'</code>                                              |
| **`listener`**  | <code>(location: <a href="#location">Location</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('http', ...)

```typescript
addListener(eventName: 'http', listener: (event: HttpEvent) => void) => Promise<PluginListenerHandle>
```

| Param           | Type                                                                |
| --------------- | ------------------------------------------------------------------- |
| **`eventName`** | <code>'http'</code>                                                 |
| **`listener`**  | <code>(event: <a href="#httpevent">HttpEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('enabledchange', ...)

```typescript
addListener(eventName: 'enabledchange', listener: (event: { enabled: boolean; }) => void) => Promise<PluginListenerHandle>
```

| Param           | Type                                                   |
| --------------- | ------------------------------------------------------ |
| **`eventName`** | <code>'enabledchange'</code>                           |
| **`listener`**  | <code>(event: { enabled: boolean; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### State

| Prop                 | Type                                                              | Description                                                                                                                                                |
| -------------------- | ----------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`url`**            | <code>string</code>                                               |                                                                                                                                                            |
| **`headers`**        | <code><a href="#record">Record</a>&lt;string, string&gt;</code>   |                                                                                                                                                            |
| **`params`**         | <code><a href="#record">Record</a>&lt;string, unknown&gt;</code>  |                                                                                                                                                            |
| **`notification`**   | <code><a href="#notificationconfig">NotificationConfig</a></code> |                                                                                                                                                            |
| **`enabled`**        | <code>boolean</code>                                              | Whether tracking is currently enabled.                                                                                                                     |
| **`lastLocationAt`** | <code>number</code>                                               | Epoch ms of the last recorded location (0 = none). Use it as a watchdog: if tracking is enabled but this is old, the OS probably killed/throttled the app. |


#### NotificationConfig

| Prop              | Type                | Description                                                                                                           |
| ----------------- | ------------------- | --------------------------------------------------------------------------------------------------------------------- |
| **`title`**       | <code>string</code> | Title of the foreground-service notification (Android). @default "Tracking location"                                  |
| **`text`**        | <code>string</code> | Text of the foreground-service notification (Android). @default "<a href="#location">Location</a> tracking is active" |
| **`channelName`** | <code>string</code> | Name of the notification channel (Android). @default "<a href="#location">Location</a> tracking"                      |
| **`smallIcon`**   | <code>string</code> | Name of a drawable/mipmap resource used as the small icon (Android).                                                  |


#### Config

| Prop                                | Type                                                              | Description                                                                                                                                                                                                                                                                                                                        |
| ----------------------------------- | ----------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`desiredAccuracy`**               | <code><a href="#desiredaccuracy">DesiredAccuracy</a></code>       | <a href="#location">Location</a> accuracy. @default "high"                                                                                                                                                                                                                                                                         |
| **`distanceFilter`**                | <code>number</code>                                               | Minimum distance (meters) the device must move before a new location is recorded. @default 10                                                                                                                                                                                                                                      |
| **`locationUpdateInterval`**        | <code>number</code>                                               | Desired interval between location updates, in ms (Android). @default 5000                                                                                                                                                                                                                                                          |
| **`fastestLocationUpdateInterval`** | <code>number</code>                                               | Fastest accepted interval between location updates, in ms (Android). @default 2000                                                                                                                                                                                                                                                 |
| **`maxAccuracy`**                   | <code>number</code>                                               | Drop fixes whose horizontal accuracy (meters) is worse than this value. 0 disables. @default 100                                                                                                                                                                                                                                   |
| **`stopOnTerminate`**               | <code>boolean</code>                                              | Stop tracking when the app is swiped away / terminated. @default false                                                                                                                                                                                                                                                             |
| **`startOnBoot`**                   | <code>boolean</code>                                              | Resume tracking after device reboot (Android). @default true                                                                                                                                                                                                                                                                       |
| **`heartbeatInterval`**             | <code>number</code>                                               | Seconds. If &gt; 0 and no location was recorded for this long (e.g. the courier stands still and distanceFilter produces no updates), a point flagged `heartbeat: true` is recorded and uploaded. 0 disables. @default 0                                                                                                           |
| **`url`**                           | <code>string</code>                                               | Server endpoint that receives the locations. If empty, locations are only stored locally. The host must be in the native allow-list (Android manifest meta-data `com.hulafo.bm_geolocation.ALLOWED_HOSTS`, iOS Info.plist `BMGeolocationAllowedHosts`) if one is configured; otherwise the call is rejected with HOST_NOT_ALLOWED. |
| **`allowCleartext`**                | <code>boolean</code>                                              | Allow plain http:// endpoints. Development only: by default only https:// is accepted. @default false                                                                                                                                                                                                                              |
| **`method`**                        | <code>'POST' \| 'PUT'</code>                                      | HTTP method. @default "POST"                                                                                                                                                                                                                                                                                                       |
| **`headers`**                       | <code><a href="#record">Record</a>&lt;string, string&gt;</code>   | Extra HTTP headers (e.g. Authorization). Stored encrypted (Android Keystore / iOS Keychain). Values are never returned to JS: getState() shows "***" instead, and sending "***" back keeps the stored value.                                                                                                                       |
| **`params`**                        | <code><a href="#record">Record</a>&lt;string, unknown&gt;</code>  | Extra properties merged into the root of every request body (e.g. courier_id, order_id).                                                                                                                                                                                                                                           |
| **`httpRootProperty`**              | <code>string</code>                                               | Name of the root property holding the location(s). @default "location"                                                                                                                                                                                                                                                             |
| **`autoSync`**                      | <code>boolean</code>                                              | Upload locations automatically as they are recorded. @default true                                                                                                                                                                                                                                                                 |
| **`batchSync`**                     | <code>boolean</code>                                              | Send several locations in one request (array under httpRootProperty). @default false                                                                                                                                                                                                                                               |
| **`maxBatchSize`**                  | <code>number</code>                                               | Maximum number of locations per batch request. @default 100                                                                                                                                                                                                                                                                        |
| **`autoSyncThreshold`**             | <code>number</code>                                               | Wait until this many locations are stored before auto-syncing. @default 0                                                                                                                                                                                                                                                          |
| **`httpTimeout`**                   | <code>number</code>                                               | HTTP timeout in ms. @default 15000                                                                                                                                                                                                                                                                                                 |
| **`maxRecordsToPersist`**           | <code>number</code>                                               | Maximum number of locations kept in the local database. @default 10000                                                                                                                                                                                                                                                             |
| **`maxDaysToPersist`**              | <code>number</code>                                               | Maximum age in days of locations kept in the local database. @default 3                                                                                                                                                                                                                                                            |
| **`notification`**                  | <code><a href="#notificationconfig">NotificationConfig</a></code> | Foreground service notification (Android).                                                                                                                                                                                                                                                                                         |


#### Location

| Prop            | Type                                                  | Description                                                     |
| --------------- | ----------------------------------------------------- | --------------------------------------------------------------- |
| **`uuid`**      | <code>string</code>                                   |                                                                 |
| **`timestamp`** | <code>string</code>                                   | ISO-8601 timestamp of the fix.                                  |
| **`coords`**    | <code><a href="#coords">Coords</a></code>             |                                                                 |
| **`battery`**   | <code>{ level: number; is_charging: boolean; }</code> |                                                                 |
| **`sample`**    | <code>boolean</code>                                  | True for locations produced by getCurrentPosition().            |
| **`heartbeat`** | <code>boolean</code>                                  | True for points recorded by the heartbeat or by a push wake-up. |


#### Coords

| Prop            | Type                | Description                         |
| --------------- | ------------------- | ----------------------------------- |
| **`latitude`**  | <code>number</code> |                                     |
| **`longitude`** | <code>number</code> |                                     |
| **`accuracy`**  | <code>number</code> | Horizontal accuracy in meters.      |
| **`altitude`**  | <code>number</code> | Altitude in meters.                 |
| **`speed`**     | <code>number</code> | Speed in m/s (-1 if unknown).       |
| **`heading`**   | <code>number</code> | Heading in degrees (-1 if unknown). |


#### PermissionState

| Prop                     | Type                                                          | Description                                                                    |
| ------------------------ | ------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| **`location`**           | <code><a href="#permissionstatus">PermissionStatus</a></code> | Foreground (while-in-use) location.                                            |
| **`backgroundLocation`** | <code><a href="#permissionstatus">PermissionStatus</a></code> | Background ("always") location.                                                |
| **`notifications`**      | <code><a href="#permissionstatus">PermissionStatus</a></code> | Notifications (needed on Android 13+ for the foreground-service notification). |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### HttpEvent

| Prop               | Type                 | Description                              |
| ------------------ | -------------------- | ---------------------------------------- |
| **`success`**      | <code>boolean</code> |                                          |
| **`status`**       | <code>number</code>  | HTTP status code (0 on network failure). |
| **`responseText`** | <code>string</code>  |                                          |
| **`count`**        | <code>number</code>  | Number of locations in the request.      |


### Type Aliases


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


#### DesiredAccuracy

<code>'high' | 'balanced' | 'low'</code>


#### PermissionStatus

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


#### PermissionState

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>

</docgen-api>
