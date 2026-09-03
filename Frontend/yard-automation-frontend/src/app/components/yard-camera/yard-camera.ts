import { Component, ElementRef, input, signal, viewChild } from '@angular/core';
import { TrackZone } from '../track/track';

type CameraView = 'split' | 'cam1' | 'cam2';

@Component({
  selector: 'app-yard-camera',
  imports: [],
  templateUrl: './yard-camera.html',
  styleUrl: './yard-camera.scss',
})
export class YardCamera {
  readonly camera1Src = input<string>('');
  readonly camera2Src = input<string>('');
  readonly isLive = input<boolean>(true);
  readonly trackZones = input<TrackZone[]>([]);

  readonly activeView = signal<CameraView>('split');
  readonly expanded = signal(false);

  private readonly video1 = viewChild<ElementRef<HTMLVideoElement>>('video1');
  private readonly video2 = viewChild<ElementRef<HTMLVideoElement>>('video2');

  setView(view: CameraView): void {
    this.activeView.set(view);
  }

  play(): void {
    this.video1()?.nativeElement.play();
    this.video2()?.nativeElement.play();
  }

  pause(): void {
    this.video1()?.nativeElement.pause();
    this.video2()?.nativeElement.pause();
  }

  onToggleExpand(): void {
    this.expanded.update((value) => !value);
  }
}
