import { WebPlugin } from '@capacitor/core';

import type {
  BundleInfo,
  SwbUpdaterPlugin,
  UpdateCheckResult,
  UpdateResult,
} from './definitions';

export class SwbUpdaterWeb extends WebPlugin implements SwbUpdaterPlugin {
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
}

