import { WebPlugin } from '@capacitor/core';

import type {
  BundleInfo,
  ErrorReportOptions,
  FeatureCheckResult,
  SwbUpdaterPlugin,
  UpdateCheckResult,
  UpdateResult,
  UserContext,
} from './definitions';

export class SwbUpdaterWeb extends WebPlugin implements SwbUpdaterPlugin {
  private userContext: UserContext = {};
  private localFeatures: Record<string, boolean> = {};

  async checkForUpdate(): Promise<UpdateCheckResult> {
    throw this.unavailable(
      'Fitur Live Update (OTA) SwbUpdater hanya didukung pada platform Android.',
    );
  }

  async sync(): Promise<UpdateResult> {
    throw this.unavailable(
      'Fitur Live Update (OTA) SwbUpdater hanya didukung pada platform Android.',
    );
  }

  async getCurrentBundle(): Promise<BundleInfo> {
    return {
      build: 0,
      version: 'web',
      source: 'builtin',
    };
  }

  async resetToBuiltin(): Promise<void> {
    // No-op pada platform web
    return;
  }

  async setUserContext(context: UserContext): Promise<void> {
    this.userContext = { ...context };
  }

  async getUserContext(): Promise<UserContext> {
    return { ...this.userContext };
  }

  async hasFeature(options: { name: string }): Promise<FeatureCheckResult> {
    const enabled = this.localFeatures[options.name] ?? true;
    return {
      name: options.name,
      enabled,
    };
  }

  async getFeatures(): Promise<Record<string, boolean>> {
    return { ...this.localFeatures };
  }

  async syncFeatures(): Promise<Record<string, boolean>> {
    return { ...this.localFeatures };
  }

  async reportError(options: ErrorReportOptions): Promise<{ reported: boolean }> {
    console.warn('[SwbUpdater Web Telemetry]', options);
    return { reported: true };
  }
}
