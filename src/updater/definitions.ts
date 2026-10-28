import type { PluginListenerHandle } from '@capacitor/core';

export interface SwbUpdaterConfig {
  updateUrl: string;
  autoUpdate?: boolean;
  baseBuild?: number;
  baseVersion?: string;
  allowInsecureHttp?: boolean;
}

export interface BundleInfo {
  build: number;
  version: string;
  source: 'builtin' | 'ota';
  path?: string;
}

export interface UpdateCheckResult {
  available: boolean;
  currentBuild: number;
  remoteBuild: number;
  version?: string;
  size?: number;
}

export interface UpdateResult {
  updated: boolean;
  fromBuild: number;
  toBuild?: number;
  version?: string;
}

export type UpdateState =
  | 'checking'
  | 'up-to-date'
  | 'available'
  | 'downloading'
  | 'verifying'
  | 'installing'
  | 'applying'
  | 'applied'
  | 'error';

export interface UpdateStateEvent {
  state: UpdateState;
  message?: string;
  build?: number;
  version?: string;
}

export interface SwbUpdaterPlugin {
  checkForUpdate(): Promise<UpdateCheckResult>;
  sync(): Promise<UpdateResult>;
  getCurrentBundle(): Promise<BundleInfo>;
  resetToBuiltin(): Promise<void>;
  addListener(
    eventName: 'updateStateChanged',
    listenerFunc: (event: UpdateStateEvent) => void,
  ): Promise<PluginListenerHandle>;
}

