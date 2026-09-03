import { Injectable, signal } from '@angular/core';

import { ActivityLogEntry } from '../../shared/activity-log/activity-log';

const MAX_LOG_ENTRIES = 100;

@Injectable({ providedIn: 'root' })
export class ActivityLogService {
  private readonly _logs = signal<ActivityLogEntry[]>([]);
  readonly logs = this._logs.asReadonly();

  logSystemConnected(): void {
    this.add('Connected to system', 'info');
  }

  logYardConnected(yardLabel: string): void {
    this.add(`Connected to ${yardLabel}`, 'info');
  }

  logZoneOccupied(zoneLabel: string): void {
    this.add(`${zoneLabel} is occupied`, 'warning');
  }

  logZoneClear(zoneLabel: string): void {
    this.add(`${zoneLabel} is clear.`, 'info');
  }

  logBoundaryBreach(): void {
    this.add('Train approaching boundary – STOP IMMEDIATELY', 'critical');
  }

  logConnectionError(): void {
    this.add('Connection error', 'critical');
  }

  logNoActiveWarnings(): void {
    this.add('No active warnings', 'info');
  }

  clear(): void {
    this._logs.set([]);
  }

  private add(message: string, severity: ActivityLogEntry['severity']): void {
    const entry: ActivityLogEntry = { time: new Date().toLocaleTimeString(), message, severity };
    this._logs.update((logs) => [...logs, entry].slice(-MAX_LOG_ENTRIES));
  }
}
