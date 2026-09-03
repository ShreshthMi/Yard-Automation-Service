import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { AudioService } from '../../services/audio/audio.service';

@Component({
  selector: 'app-alert',
  imports: [],
  templateUrl: './alert.html',
  styleUrl: './alert.scss',
})
export class Alert implements OnInit, OnDestroy {
  private readonly audioService = inject(AudioService);

  ngOnInit(): void {
    //this.audioService.beep(Infinity, 150);
    //this.audioService.beep(3, 150);
  }

  ngOnDestroy(): void {
    //this.audioService.stop();
  }
}
