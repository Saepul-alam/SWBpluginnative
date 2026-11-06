import type { PluginListenerHandle } from '@capacitor/core';

export interface UserContext {
  userId?: string;
  branch?: string;
  role?: string;
}

export interface FeatureCheckResult {
  name: string;
  enabled: boolean;
}

export interface ErrorReportOptions {
  feature?: string;
  error: string;
  metadata?: Record<string, any>;
}

export interface SwbUpdaterConfig {
  updateUrl: string;
  autoUpdate?: boolean;
  baseBuild?: number;
  baseVersion?: string;
  allowInsecureHttp?: boolean;
  telemetryUrl?: string;
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

  // --- Feature Management & Context APIs ---
  setUserContext(context: UserContext): Promise<void>;
  getUserContext(): Promise<UserContext>;
  hasFeature(options: { name: string }): Promise<FeatureCheckResult>;
  getFeatures(): Promise<Record<string, boolean>>;
  syncFeatures(): Promise<Record<string, boolean>>;
  reportError(options: ErrorReportOptions): Promise<{ reported: boolean }>;

  addListener(
    eventName: 'updateStateChanged',
    listenerFunc: (event: UpdateStateEvent) => void,
  ): Promise<PluginListenerHandle>;
}
