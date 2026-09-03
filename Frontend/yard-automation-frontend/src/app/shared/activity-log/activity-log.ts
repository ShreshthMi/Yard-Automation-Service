import { Component, ElementRef, computed, effect, input, signal, viewChild } from '@angular/core';
import { Message } from '../message/message';

export interface ActivityLogEntry {
  time: string;
  message: string;
  severity: 'info' | 'warning' | 'critical';
}

@Component({
  selector: 'app-activity-log',
  imports: [Message],
  templateUrl: './activity-log.html',
  styleUrl: './activity-log.scss',
})
export class ActivityLog {
  readonly title = input<string>('RECENT ACTIVITY');
  readonly available = input<boolean>(true);
  readonly logs = input<ActivityLogEntry[]>([]);

  private readonly cleared = signal(false);
  readonly expanded = signal(false);

  private readonly logsContainer = viewChild<ElementRef<HTMLElement>>('logsContainer');

  readonly visibleLogs = computed(() => {
    if (this.cleared()) {
      return [];
    }
    const logs = this.logs();
    return this.expanded() ? logs : logs.slice(-3);
  });

  constructor() {
    effect(() => {
      const count = this.visibleLogs().length;
      const container = this.logsContainer()?.nativeElement;
      if (this.expanded() && container && count > 0) {
        container.scrollTop = container.scrollHeight;
      }
    });
  }

  onDelete(): void {
    this.cleared.set(true);
  }

  onToggleExpand(): void {
    this.expanded.update((value) => !value);
  }
}
