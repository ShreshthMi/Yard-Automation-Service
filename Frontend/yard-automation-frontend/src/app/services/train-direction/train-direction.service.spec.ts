import { TestBed } from '@angular/core/testing';

import { TrainDirectionService } from './train-direction.service';

describe('TrainDirectionService', () => {
  let service: TrainDirectionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TrainDirectionService);
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should default to forward', () => {
    expect(service.direction()).toBe('forward');
  });

  it('should stay forward when only establishing the initial baseline', () => {
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 18 },
      { name: 'ZONE 2', axleCount: 0 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);

    expect(service.direction()).toBe('forward');
  });

  it('should ignore updates where no zone is occupied', () => {
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 18 },
      { name: 'ZONE 2', axleCount: 0 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 0 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);

    expect(service.direction()).toBe('forward');
  });

  it('should report forward when the leading occupied zone advances', () => {
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 18 },
      { name: 'ZONE 2', axleCount: 0 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 18 },
      { name: 'ZONE 2', axleCount: 6 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);

    expect(service.direction()).toBe('forward');
  });

  it('should report forward when the axle count keeps growing within the leading zone', () => {
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 4 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 10 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);

    expect(service.direction()).toBe('forward');
  });

  it('should report reverse when the leading occupied zone falls back', () => {
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 6 },
      { name: 'ZONE 3', axleCount: 12 },
    ]);
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 6 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);

    expect(service.direction()).toBe('reverse');
  });

  it('should report reverse when the axle count shrinks within the leading zone', () => {
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 10 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 4 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);

    expect(service.direction()).toBe('reverse');
  });

  it('should reset back to the default forward direction', () => {
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 12 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);
    service.updateFromZones([
      { name: 'ZONE 1', axleCount: 0 },
      { name: 'ZONE 2', axleCount: 4 },
      { name: 'ZONE 3', axleCount: 0 },
    ]);
    expect(service.direction()).toBe('reverse');

    service.reset();

    expect(service.direction()).toBe('forward');
  });
});
