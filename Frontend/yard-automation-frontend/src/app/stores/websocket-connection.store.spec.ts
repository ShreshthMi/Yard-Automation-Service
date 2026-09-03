import { TestBed } from '@angular/core/testing';

import { WebsocketConnectionStore } from './websocket-connection.store';
import { WebsocketConnectionService } from '../services/websocket-connection/websocket-connection.service';

describe('WebsocketConnectionStore', () => {
  let store: WebsocketConnectionStore;
  let websocketConnectionService: WebsocketConnectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(WebsocketConnectionStore);
    websocketConnectionService = TestBed.inject(WebsocketConnectionService);
  });

  it('should create', () => {
    expect(store).toBeTruthy();
  });

  it('should mark the connection as connected on success', async () => {
    vi.spyOn(websocketConnectionService, 'connect').mockResolvedValue();

    await store.connect();

    expect(store.state()).toBe('connected');
    expect(store.error()).toBeNull();
  });

  it('should set an error state when the connection fails', async () => {
    vi.spyOn(websocketConnectionService, 'connect').mockRejectedValue(new Error('WebSocket connection failed'));

    await store.connect();

    expect(store.state()).toBe('error');
    expect(store.error()).toBe('WebSocket connection failed');
  });

  it('should append incoming messages to the message list', async () => {
    vi.spyOn(websocketConnectionService, 'connect').mockImplementation(async (onMessage) => {
      onMessage?.({ status: 'ok' });
    });

    await store.connect();

    expect(store.messages()).toEqual([{ status: 'ok' }]);
  });

  it('should reset to idle on disconnect', () => {
    const disconnectSpy = vi.spyOn(websocketConnectionService, 'disconnect').mockImplementation(() => {});

    store.disconnect();

    expect(disconnectSpy).toHaveBeenCalled();
    expect(store.state()).toBe('idle');
  });
});
