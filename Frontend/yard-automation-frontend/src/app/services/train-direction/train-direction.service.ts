import { Injectable, signal } from '@angular/core';

export type TrainDirection = 'forward' | 'reverse';

export interface ZoneAxleSnapshot {
  name: string;
  axleCount: number;
}

/**
 * Derives the train's direction of travel from per-zone axle counts (e.g. as
 * reported over the yard websocket connection). Zones must be passed in
 * their physical forward order (zone 1 first).
 *
 * There is no dedicated direction sensor, so direction is inferred from how
 * the "leading edge" - the furthest occupied zone - moves between updates:
 *  - it advances to a later zone, or its axle count keeps growing -> forward
 *  - it falls back to an earlier zone, or its axle count shrinks -> reverse
 */
@Injectable({ providedIn: 'root' })
export class TrainDirectionService {
  private readonly _direction = signal<TrainDirection>('forward');
  readonly direction = this._direction.asReadonly();

  private previousLeadingZoneIndex = -1;
  private previousLeadingZoneAxleCount = 0;

  updateFromZones(zones: readonly ZoneAxleSnapshot[]): TrainDirection {
    const leadingZoneIndex = this.findLeadingZoneIndex(zones);

    if (leadingZoneIndex === -1) {
      return this._direction();
    }

    const leadingZoneAxleCount = zones[leadingZoneIndex].axleCount;

    if (this.previousLeadingZoneIndex !== -1) {
      if (leadingZoneIndex > this.previousLeadingZoneIndex) {
        this._direction.set('forward');
      } else if (leadingZoneIndex < this.previousLeadingZoneIndex) {
        this._direction.set('reverse');
      } else if (leadingZoneAxleCount > this.previousLeadingZoneAxleCount) {
        this._direction.set('forward');
      } else if (leadingZoneAxleCount < this.previousLeadingZoneAxleCount) {
        this._direction.set('reverse');
      }
    }

    this.previousLeadingZoneIndex = leadingZoneIndex;
    this.previousLeadingZoneAxleCount = leadingZoneAxleCount;

    return this._direction();
  }

  /** Resets to the default forward direction, e.g. once a train fully clears the yard. */
  reset(): void {
    this._direction.set('forward');
    this.previousLeadingZoneIndex = -1;
    this.previousLeadingZoneAxleCount = 0;
  }

  private findLeadingZoneIndex(zones: readonly ZoneAxleSnapshot[]): number {
    for (let i = zones.length - 1; i >= 0; i--) {
      if (zones[i].axleCount > 0) {
        return i;
      }
    }
    return -1;
  }
}
