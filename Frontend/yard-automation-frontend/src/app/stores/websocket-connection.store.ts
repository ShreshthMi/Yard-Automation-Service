import { Injectable, inject, signal } from '@angular/core';

import { WebsocketConnectionService } from '../services/websocket-connection/websocket-connection.service';

export type WebsocketConnectionState = 'idle' | 'connecting' | 'connected' | 'error';

@Injectable({ providedIn: 'root' })
export class WebsocketConnectionStore {
  private readonly websocketConnectionService = inject(WebsocketConnectionService);

  private readonly _state = signal<WebsocketConnectionState>('idle');
  private readonly _messages = signal<unknown[]>([]);
  private readonly _error = signal<string | null>(null);

  readonly state = this._state.asReadonly();
  readonly messages = this._messages.asReadonly();
  readonly error = this._error.asReadonly();

  async connect(): Promise<void> {
    this._state.set('connecting');
    this._error.set(null);

    try {
      await this.websocketConnectionService.connect((data) => {
        this._messages.update((messages) => [...messages, data]);
      });
      this._state.set('connected');
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Failed to connect to websocket');
      this._state.set('error');
    }
  }

  send(data: unknown): void {
    this.websocketConnectionService.send(data);
  }

  disconnect(): void {
    this.websocketConnectionService.disconnect();
    this._state.set('idle');
  }
}
