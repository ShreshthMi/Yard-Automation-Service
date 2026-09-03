import { ComponentFixture, TestBed } from '@angular/core/testing';

import { YardCamera } from './yard-camera';

describe('YardCamera', () => {
  let component: YardCamera;
  let fixture: ComponentFixture<YardCamera>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [YardCamera],
    }).compileComponents();

    fixture = TestBed.createComponent(YardCamera);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
