import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { InitialConnectionService, InitialConnectionStatus } from './initial-connection.service';
import { environment } from '../../environments/environment';

describe('InitialConnectionService', () => {
  let service: InitialConnectionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InitialConnectionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should resolve with the initial connection status from the api', async () => {
    const mockStatus: InitialConnectionStatus = { connected: true, message: 'Connected to yard controller' };

    const result = service.getInitialConnection();

    const req = httpMock.expectOne(`${environment.apiUrl}/initial-connection`);
    expect(req.request.method).toBe('GET');
    req.flush(mockStatus);

    await expect(result).resolves.toEqual(mockStatus);
  });
});
