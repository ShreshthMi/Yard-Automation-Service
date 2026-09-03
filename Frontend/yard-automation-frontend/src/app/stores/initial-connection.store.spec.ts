import { TestBed } from '@angular/core/testing';

import { InitialConnectionStore } from './initial-connection.store';
import { InitialConnectionService, InitialConnectionStatus } from '../services/initial-connection/initial-connection.service';

describe('InitialConnectionStore', () => {
  let store: InitialConnectionStore;
  let initialConnectionService: InitialConnectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(InitialConnectionStore);
    initialConnectionService = TestBed.inject(InitialConnectionService);
  });

  it('should create', () => {
    expect(store).toBeTruthy();
  });

  it('should populate the status on a successful load', async () => {
    const mockStatus: InitialConnectionStatus = { connected: true, message: 'Connected to yard controller' };
    vi.spyOn(initialConnectionService, 'getInitialConnection').mockResolvedValue(mockStatus);

    await store.load();

    expect(store.status()).toEqual(mockStatus);
    expect(store.loading()).toBe(false);
    expect(store.error()).toBeNull();
  });

  it('should set an error message when the load fails', async () => {
    vi.spyOn(initialConnectionService, 'getInitialConnection').mockRejectedValue(new Error('network down'));

    await store.load();

    expect(store.error()).toBe('network down');
    expect(store.loading()).toBe(false);
    expect(store.status()).toBeNull();
  });
});
