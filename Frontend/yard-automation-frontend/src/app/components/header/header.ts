import { DatePipe } from '@angular/common';
import { Component, DestroyRef, signal } from '@angular/core';

@Component({
  selector: 'app-header',
  imports: [DatePipe],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  readonly now = signal(new Date());

  constructor(destroyRef: DestroyRef) {
    const intervalId = setInterval(() => this.now.set(new Date()), 1000);
    destroyRef.onDestroy(() => clearInterval(intervalId));
  }
}
