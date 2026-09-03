import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';

import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;
  let messageService: MessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MessageService],
    });
    service = TestBed.inject(ToastService);
    messageService = TestBed.inject(MessageService);
  });

  it('should create', () => {
    expect(service).toBeTruthy();
  });

  it('should add a success message', () => {
    const addSpy = vi.spyOn(messageService, 'add');
    service.showSuccess('Your changes have been saved.');
    expect(addSpy).toHaveBeenCalledWith({
      severity: 'success',
      summary: 'Success',
      detail: 'Your changes have been saved.',
    });
  });

  it('should add an error message', () => {
    const addSpy = vi.spyOn(messageService, 'add');
    service.showError('We could not complete the action.');
    expect(addSpy).toHaveBeenCalledWith({
      severity: 'error',
      summary: 'Error',
      detail: 'We could not complete the action.',
    });
  });
});
