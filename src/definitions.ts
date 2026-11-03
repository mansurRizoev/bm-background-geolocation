export interface bm_geolocationPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
