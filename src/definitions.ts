export interface SwbUpdaterPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
