import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NoWarning } from './no-warning';

describe('NoWarning', () => {
  let component: NoWarning;
  let fixture: ComponentFixture<NoWarning>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NoWarning],
    }).compileComponents();

    fixture = TestBed.createComponent(NoWarning);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
