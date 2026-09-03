import { Component, effect, input, signal } from '@angular/core';

export interface TrackZone {
  name: string;
  widthPercent: number;
  status: 'occupied' | 'clear';
  axleCount: number;
  boundary?: boolean;
}

const DISTANCE_VISIBLE_MS = 5000;
const ZONE_DISTANCES_METERS = [300, 200, 100];

@Component({
  selector: 'app-track',
  imports: [],
  templateUrl: './track.html',
  styleUrl: './track.scss',
})
export class Track {
  readonly zones = input<TrackZone[]>([
    { name: 'ZONE 1', widthPercent: 40, status: 'clear', axleCount: 0 },
    { name: 'ZONE 2', widthPercent: 35, status: 'clear', axleCount: 0 },
    { name: 'ZONE 3', widthPercent: 25, status: 'clear', axleCount: 0, boundary: true },
  ]);

  private readonly previousStatus = new Map<string, TrackZone['status']>();
  private readonly hideTimers = new Map<string, ReturnType<typeof setTimeout>>();
  private readonly visibleDistanceZones = signal<ReadonlySet<string>>(new Set());

  constructor() {
    effect(() => {
      for (const zone of this.zones()) {
        const wasOccupied = this.previousStatus.get(zone.name) === 'occupied';
        if (zone.status === 'occupied' && !wasOccupied) {
          this.showDistance(zone.name);
        }
        this.previousStatus.set(zone.name, zone.status);
      }
    });
  }

  isDistanceVisible(name: string): boolean {
    return this.visibleDistanceZones().has(name);
  }

  distanceMeters(i: number): number {
    return ZONE_DISTANCES_METERS[i] ?? 0;
  }

  private showDistance(name: string): void {
    const existingTimer = this.hideTimers.get(name);
    if (existingTimer) {
      clearTimeout(existingTimer);
    }

    this.visibleDistanceZones.update((zones) => new Set(zones).add(name));

    this.hideTimers.set(
      name,
      setTimeout(() => {
        this.visibleDistanceZones.update((zones) => {
          const next = new Set(zones);
          next.delete(name);
          return next;
        });
        this.hideTimers.delete(name);
      }, DISTANCE_VISIBLE_MS),
    );
  }
}
