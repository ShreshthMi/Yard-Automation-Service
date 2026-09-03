import { TestBed } from '@angular/core/testing';

import { WebsocketConnectionService } from './websocket-connection.service';

class MockWebSocket {
  static readonly OPEN = 1;
  static readonly CLOSED = 3;
  static instances: MockWebSocket[] = [];

  readyState = 0;
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: (() => void) | null = null;
  sent: string[] = [];

  constructor(readonly url: string) {
    MockWebSocket.instances.push(this);
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.();
  }

  triggerOpen(): void {
    this.readyState = MockWebSocket.OPEN;
    this.onopen?.();
  }

  triggerMessage(data: unknown): void {
    this.onmessage?.({ data: JSON.stringify(data) } as MessageEvent);
  }

  triggerError(): void {
    this.onerror?.();
  }
}

describe('WebsocketConnectionService', () => {
  let service: WebsocketConnectionService;
  let originalWebSocket: typeof WebSocket;

  beforeEach(() => {
    originalWebSocket = globalThis.WebSocket;
    MockWebSocket.instances = [];
    globalThis.WebSocket = MockWebSocket as unknown as typeof WebSocket;

    TestBed.configureTestingModule({});
    service = TestBed.inject(WebsocketConnectionService);
  });

  afterEach(() => {
    globalThis.WebSocket = originalWebSocket;
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should resolve once the socket opens and forward messages', async () => {
    const messages: unknown[] = [];
    const connectPromise = service.connect((data) => messages.push(data));

    const socket = MockWebSocket.instances[0];
    socket.triggerOpen();
    await connectPromise;

    socket.triggerMessage({ status: 'ok' });
    expect(messages).toEqual([{ status: 'ok' }]);
  });

  it('should reject when the socket errors', async () => {
    const connectPromise = service.connect();

    const socket = MockWebSocket.instances[0];
    socket.triggerError();

    await expect(connectPromise).rejects.toThrow('WebSocket connection failed');
  });

  it('should send data once connected and throw otherwise', async () => {
    expect(() => service.send({ ping: true })).toThrow('WebSocket is not connected');

    const connectPromise = service.connect();
    MockWebSocket.instances[0].triggerOpen();
    await connectPromise;

    service.send({ ping: true });
    expect(MockWebSocket.instances[0].sent).toEqual([JSON.stringify({ ping: true })]);
  });

  it('should close the socket on disconnect', async () => {
    const connectPromise = service.connect();
    const socket = MockWebSocket.instances[0];
    socket.triggerOpen();
    await connectPromise;

    service.disconnect();
    expect(socket.readyState).toBe(MockWebSocket.CLOSED);
  });
});
