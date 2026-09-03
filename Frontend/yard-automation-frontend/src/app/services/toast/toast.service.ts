import { Injectable, inject } from '@angular/core';
import { MessageService } from 'primeng/api';

@Injectable({ providedIn: 'root' })
export class ToastService {
  private messageService = inject(MessageService);

  showSuccess(detail: string, summary = 'Success'): void {
    this.messageService.add({ severity: 'success', summary, detail });
  }

  showError(detail: string, summary = 'Error'): void {
    this.messageService.add({ severity: 'error', summary, detail });
  }
}
