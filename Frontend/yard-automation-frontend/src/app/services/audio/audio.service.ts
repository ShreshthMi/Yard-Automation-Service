import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AudioService {
  private context: AudioContext | null = null;
  private continuousHandle: ReturnType<typeof setInterval> | null = null;

  /**
   * Plays one or more beep tones.
   * @param count Number of beeps to play. Pass Infinity to beep continuously until stop() is called. Defaults to 1.
   * @param speed Milliseconds between the start of each beep. Defaults to 300.
   */
  beep(count = 1, speed = 300): void {
    this.stop();

    if (count === Infinity) {
      this.playTone(speed);
      this.continuousHandle = setInterval(() => this.playTone(speed), speed);
      return;
    }

    try {
      const ctx = this.ensureAudioContext();
      const fireTones = () => this.scheduleTones(ctx, count, speed);

      if (ctx.state === 'suspended') {
        ctx.resume().then(fireTones);
      } else {
        fireTones();
      }
    } catch (e) {
      console.warn('Could not play beep: ' + (e as Error).message);
    }
  }

  /**
   * Stops beeping started with beep(Infinity, speed).
   */
  stop(): void {
    if (this.continuousHandle) {
      clearInterval(this.continuousHandle);
      this.continuousHandle = null;
    }
  }

  private playTone(speed: number): void {
    try {
      const ctx = this.ensureAudioContext();
      const fireTone = () => this.scheduleTones(ctx, 1, speed);

      if (ctx.state === 'suspended') {
        ctx.resume().then(fireTone);
      } else {
        fireTone();
      }
    } catch (e) {
      console.warn('Could not play beep: ' + (e as Error).message);
    }
  }

  private ensureAudioContext(): AudioContext {
    if (!this.context || this.context.state === 'closed') {
      this.context = new AudioContext();
    }
    return this.context;
  }

  private scheduleTones(ctx: AudioContext, count: number, speed: number): void {
    const intervalSeconds = Math.max(speed, 0) / 1000;
    const toneDuration = intervalSeconds > 0 ? Math.min(0.25, intervalSeconds * 0.8) : 0.25;

    for (let i = 0; i < count; i++) {
      const startTime = ctx.currentTime + i * intervalSeconds;

      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      osc.type = 'square';
      osc.frequency.value = 1000;

      gain.gain.setValueAtTime(0.7, startTime);

      osc.connect(gain);
      gain.connect(ctx.destination);

      osc.start(startTime);
      osc.stop(startTime + toneDuration);
    }
  }
}
