import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ServerConnectionError } from './server-connection-error';

describe('ServerConnectionError', () => {
  let component: ServerConnectionError;
  let fixture: ComponentFixture<ServerConnectionError>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ServerConnectionError],
    }).compileComponents();

    fixture = TestBed.createComponent(ServerConnectionError);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
