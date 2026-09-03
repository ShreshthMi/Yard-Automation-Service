import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Warning } from './warning';

describe('Warning', () => {
  let component: Warning;
  let fixture: ComponentFixture<Warning>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Warning],
    }).compileComponents();

    fixture = TestBed.createComponent(Warning);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
