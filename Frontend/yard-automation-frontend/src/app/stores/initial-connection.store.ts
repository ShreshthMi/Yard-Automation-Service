import { Injectable, inject, signal } from '@angular/core';

import {
  InitialConnectionService,
  InitialConnectionStatus,
} from '../services/initial-connection/initial-connection.service';

@Injectable({ providedIn: 'root' })
export class InitialConnectionStore {
  private readonly initialConnectionService = inject(InitialConnectionService);

  private readonly _status = signal<InitialConnectionStatus | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly status = this._status.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  async load(): Promise<void> {
    this._loading.set(true);
    this._error.set(null);

    try {
      const status = await this.initialConnectionService.getInitialConnection();
      this._status.set(status);
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Failed to load initial connection');
    } finally {
      this._loading.set(false);
    }
  }
}
