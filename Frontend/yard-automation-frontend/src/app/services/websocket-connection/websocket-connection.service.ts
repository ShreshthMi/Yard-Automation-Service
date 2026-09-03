import { Injectable } from '@angular/core';

import { environment } from '../../environments/environment';

export type WebSocketMessageHandler = (data: unknown) => void;

@Injectable({ providedIn: 'root' })
export class WebsocketConnectionService {
  private socket: WebSocket | null = null;

  connect(onMessage?: WebSocketMessageHandler): Promise<void> {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(environment.wsUrl);

      socket.onopen = () => {
        this.socket = socket;
        resolve();
      };

      socket.onerror = () => {
        reject(new Error('WebSocket connection failed'));
      };

      socket.onmessage = (event: MessageEvent) => {
        onMessage?.(JSON.parse(event.data));
      };

      socket.onclose = () => {
        this.socket = null;
      };
    });
  }

  send(data: unknown): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket is not connected');
    }
    this.socket.send(JSON.stringify(data));
  }

  disconnect(): void {
    this.socket?.close();
    this.socket = null;
  }
}
