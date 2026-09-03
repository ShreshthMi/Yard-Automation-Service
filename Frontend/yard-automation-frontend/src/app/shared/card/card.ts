import { Component, input } from '@angular/core';

@Component({
  selector: 'app-card',
  imports: [],
  templateUrl: './card.html',
  styleUrl: './card.scss',
  host: {
    '[style.height]': 'height()',
    '[style.width]': 'width()',
  },
})
export class Card {
  readonly title = input<string>('');
  readonly height = input<string>('auto');
  readonly width = input<string>('auto');
  readonly showStatus = input<boolean>(false);
  readonly online = input<boolean>(false);
  readonly onlineLabel = input<string>('Online');
  readonly offlineLabel = input<string>('Offline');
  readonly uptime = input<string>('');
  readonly showDirection = input<boolean>(false);
  readonly direction = input<'forward' | 'reverse'>('forward');
}
