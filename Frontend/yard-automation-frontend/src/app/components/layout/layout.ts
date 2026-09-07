import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ToastService } from '../../services/toast/toast.service';
import { InitialConnectionService } from '../../services/initial-connection/initial-connection.service';
import { Card } from '../../shared/card/card';
import {Message} from '../../shared/message/message';
import { Select } from '../../shared/select/select';
import {Loader} from '../../shared/loader/loader';
import {ServerConnectionError} from '../../shared/server-connection-error/server-connection-error';
import { ActivityLog } from '../../shared/activity-log/activity-log';
import { InitialComponent } from '../../shared/initial-component/initial-component';
import { NoWarning } from '../no-warning/no-warning';
import { Warning } from '../warning/warning';
import { Alert } from '../alert/alert';
import { YardCamera } from '../yard-camera/yard-camera';
import { Track, TrackZone } from '../track/track';
import { TrainDirectionService, TrainDirection } from '../../services/train-direction/train-direction.service';
import { ActivityLogService } from '../../services/activity-log/activity-log.service';
import { WebsocketConnectionService } from '../../services/websocket-connection/websocket-connection.service';
import { AudioService } from '../../services/audio/audio.service';

interface Yard {
  name: string;
  code: string;
}

@Component({
  selector: 'app-layout',
  imports: [Card, Message, Select, Loader, ServerConnectionError, InitialComponent, ActivityLog, NoWarning, Warning, Alert, YardCamera, Track],
  templateUrl: './layout.html',
  styleUrl: './layout.scss',
})
class Layout implements OnInit, OnDestroy {

  camera1Source = '';
  camera2Source = '';

  // TEMPORARY: demo calls for ToastService, remove once wired to real events.
  private toast = inject(ToastService);
  private trainDirectionService = inject(TrainDirectionService);
  private activityLogService = inject(ActivityLogService);
  private websocketConnectionService = inject(WebsocketConnectionService);
  private audioService = inject(AudioService);
  private initialConnectionService = inject(InitialConnectionService);

  async ngOnInit(): Promise<void> {
    //this.toast.showSuccess('Yard 01 is now connected');
    //this.toast.showError('Failed to connect to yard. Please try again.');
    this.trainDirectionService.updateFromZones(this.trackZones);
  }

  ngOnDestroy(): void {
    this.stopUptimeTimer();
  }

  private async connectToSystem(yard: Yard): Promise<void> {
    this.isConnecting = true;
    this.connectionFailed = false;
    try {
      const status = await this.initialConnectionService.connect({
        yard,
        targetIpAddress: this.targetIpAddress,
        // localIpAddress: this.localIpAddress,
      });
      if (!status.connected) {
        this.toast.showError(status.message || 'Failed to connect to yard. Please try again.');
        this.activityLogService.logConnectionError();
        this.isConnecting = false;
        this.showInitialPrompt = true;
        return;
      }
      this.activityLogService.logSystemConnected();
      this.selectedYard = yard;
      this.connectWebsocket(
        yard,
        () => {
          this.isConnecting = false;
        },
        () => {
          this.isConnecting = false;
          this.showInitialPrompt = true;
        }
      );
    } catch {
      this.toast.showError('Failed to connect to yard. Please try again.');
      this.activityLogService.logConnectionError();
      this.isConnecting = false;
      this.showInitialPrompt = true;
    }
  }

  private setOnlineStatus(online: boolean): void {
    this.isOnline = online;
    if (online) {
      this.startUptimeTimer();
    } else {
      this.stopUptimeTimer();
    }
  }

  private startUptimeTimer(): void {
    this.stopUptimeTimer();
    this.connectedAt = Date.now();
    this.updateUptime();
    this.uptimeIntervalId = setInterval(() => this.updateUptime(), 1000);
  }

  private stopUptimeTimer(): void {
    if (this.uptimeIntervalId !== undefined) {
      clearInterval(this.uptimeIntervalId);
      this.uptimeIntervalId = undefined;
    }
    this.uptime = '00:00';
  }

  private updateUptime(): void {
    const totalMinutes = Math.floor((Date.now() - this.connectedAt) / 60000);
    const hours = Math.floor(totalMinutes / 60) % 100;
    const minutes = totalMinutes % 60;
    this.uptime = `${this.pad(hours)}:${this.pad(minutes)}`;
  }

  private pad(value: number): string {
    return value.toString().padStart(2, '0');
  }

  onRetryConnection(): void {
    if (this.initialYard) {
      void this.connectToSystem(this.initialYard);
    }
  }

  private connectWebsocket(yard: Yard, onSuccess?: () => void, onError?: () => void): void {
    this.websocketConnectionService.connect((data) => this.handleWebsocketMessage(data))
      .then(() => {
        this.websocketConnected = true;
        this.websocketConnectionService.send(yard);
        this.setOnlineStatus(true);
        this.activityLogService.logYardConnected(yard.name);
        onSuccess?.();
      })
      .catch(() => {
        this.websocketConnected = false;
        this.setOnlineStatus(false);
        this.toast.showError('Failed to connect to yard. Please try again.');
        this.activityLogService.logConnectionError();
        onError?.();
      });
  }

  private handleWebsocketMessage(data: unknown): void {
    const zones = Array.isArray(data)
      ? data
      : data && typeof data === 'object' && Array.isArray((data as { zones?: unknown }).zones)
        ? (data as { zones: unknown[] }).zones
        : null;

    if (zones && zones.every((zone) => this.isTrackZone(zone))) {
      this.applyZoneUpdate(zones as TrackZone[]);
    }
  }

  private isTrackZone(value: unknown): value is TrackZone {
    if (!value || typeof value !== 'object') {
      return false;
    }
    const zone = value as Partial<TrackZone>;
    return (
      typeof zone.name === 'string' &&
      (zone.status === 'occupied' || zone.status === 'clear') &&
      typeof zone.axleCount === 'number' &&
      typeof zone.widthPercent === 'number'
    );
  }
  //Replace with API data
  yards: Yard[] = [
    { name: 'Yard 01', code: '01' }
  ];

  selectedYard?: Yard;

  onYardSelected(yard: Yard | undefined): void {
    this.selectedYard = yard;
    // if (yard) {
    //   this.connectWebsocket(yard);
    // }
  }

  showInitialPrompt = true;
  initialYard?: Yard;
  targetIpAddress = '';
  // localIpAddress = '';

  onLaunchApplication(): void {
    if (!this.initialYard) {
      return;
    }
    this.showInitialPrompt = false;
    void this.connectToSystem(this.initialYard);
  }

  isOnline = false;
  uptime = '00:00';
  private connectedAt = 0;
  private uptimeIntervalId?: ReturnType<typeof setInterval>;

  isConnecting = false;
  connectionFailed = false;

  private isZoneOccupied(name: string): boolean {
    return this.trackZones.find((zone) => zone.name === name)?.status === 'occupied';
  }

  get systemStatusNoWarning(): boolean {
    return (
      this.isOnline &&
      !this.isZoneOccupied('ZONE 1') &&
      !this.isZoneOccupied('ZONE 2') &&
      !this.isZoneOccupied('ZONE 3')
    );
  }

  get systemStatusWarning(): boolean {
    return (
      this.isOnline &&
      !this.isZoneOccupied('ZONE 3') &&
      (this.isZoneOccupied('ZONE 1') || this.isZoneOccupied('ZONE 2'))
    );
  }

  get systemStatusAlert(): boolean {
    return this.isOnline && this.isZoneOccupied('ZONE 3');
  }

  /** 10 while the train is in zone 1, drops to 5 once it enters zone 2. */
  get speedLimit(): number {
    const zone2 = this.trackZones.find((zone) => zone.name === 'ZONE 2');
    return zone2?.status === 'occupied' ? 5 : 10;
  }

  get isCameraLive(): boolean {
    return !this.showInitialPrompt && !this.isConnecting;
  }

  isCameraOnline = false;

  // Derived from per-zone axle counts by TrainDirectionService - see applyZoneUpdate().
  readonly trackDirection = this.trainDirectionService.direction;

  private websocketConnected = false;

  get isTrackAvailable(): boolean {
    return !!this.selectedYard && this.websocketConnected;
  }

  trackZones: TrackZone[] = [
    { name: 'ZONE 1', widthPercent: 40, status: 'clear', axleCount: 0 },
    { name: 'ZONE 2', widthPercent: 35, status: 'clear', axleCount: 0 },
    { name: 'ZONE 3', widthPercent: 25, status: 'clear', axleCount: 0, boundary: true },
  ];

  /** Call whenever fresh zone data (e.g. from the yard websocket) arrives. */
  applyZoneUpdate(zones: TrackZone[]): void {
    const direction = this.trainDirectionService.updateFromZones(zones);
    this.logZoneTransitions(this.trackZones, zones, direction);
    this.trackZones = zones;
  }

  private logZoneTransitions(previous: TrackZone[], next: TrackZone[], direction: TrainDirection): void {
    for (const zone of next) {
      const previousZone = previous.find((z) => z.name === zone.name);
      const wasOccupied = previousZone?.status === 'occupied';
      const label = this.formatZoneLabel(zone.name);

      if (zone.status === 'occupied' && !wasOccupied) {
        if (zone.name === 'ZONE 1') {
          this.activityLogService.logZoneOccupied(label);
          this.audioService.beep(1);
        } else if (zone.name === 'ZONE 2') {
          this.activityLogService.logZoneOccupied(label);
          this.audioService.beep(2);
        } else if (zone.name === 'ZONE 3') {
          this.activityLogService.logBoundaryBreach();
          this.audioService.beep(5);
        }
      } else if (zone.status === 'clear' && wasOccupied && direction === 'reverse') {
        if (zone.name === 'ZONE 1' || zone.name === 'ZONE 2') {
          this.activityLogService.logZoneClear(label);
        }
      }
    }
  }

  private formatZoneLabel(name: string): string {
    return name.charAt(0) + name.slice(1).toLowerCase();
  }

  showRecentActivityLog = true;
  readonly recentActivityLogs = this.activityLogService.logs;
}

export default Layout
