import { TestBed } from '@angular/core/testing';

import { AudioService } from './audio.service';

describe('AudioService', () => {
  let service: AudioService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AudioService);
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should not throw when beeping multiple times at a given speed', () => {
    expect(() => service.beep(3, 150)).not.toThrow();
  });
});
