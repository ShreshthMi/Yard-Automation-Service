import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';

export interface InitialConnectionStatus {
  connected: boolean;
  message: string;
}

export interface InitialConnectionRequest {
  yard: { name: string; code: string };
  targetIpAddress: string;
  // localIpAddress: string;
}

@Injectable({ providedIn: 'root' })
export class InitialConnectionService {
  private readonly http = inject(HttpClient);

  getInitialConnection(): Promise<InitialConnectionStatus> {
    return firstValueFrom(this.http.get<InitialConnectionStatus>(`${environment.apiUrl}/initial-connection`));
  }

  connect(payload: InitialConnectionRequest): Promise<InitialConnectionStatus> {
    return firstValueFrom(this.http.post<InitialConnectionStatus>(`${environment.apiUrl}/initial-connection`, payload));
  }
}
