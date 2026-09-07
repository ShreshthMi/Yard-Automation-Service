import { Component, computed, input, model, output, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { Select } from '../select/select';

@Component({
  selector: 'app-initial-component',
  imports: [Select, ButtonModule, FloatLabelModule, FormsModule, InputTextModule],
  templateUrl: './initial-component.html',
  styleUrl: './initial-component.scss',
})
export class InitialComponent {
  readonly title = input<string>('Select a yard');
  readonly description = input<string>('Select a yard to begin live monitoring.');

  readonly options = input<any[]>([]);
  readonly optionLabel = input<string>('name');
  readonly optionValue = input<string | undefined>(undefined);
  readonly label = input<string>('Select a yard for live monitoring');
  readonly placeholder = input<string>('');
  readonly value = input<any>(undefined);
  readonly buttonLabel = input<string>('Launch Application');

  readonly targetIpAddress = model<string>('');
  // readonly localIpAddress = model<string>('');

  readonly valueChange = output<any>();
  readonly launchApplication = output<void>();

  private readonly launchAttempted = signal(false);

  readonly selectInvalid = computed(() => this.launchAttempted() && !this.value());
  readonly targetIpInvalid = computed(() => this.launchAttempted() && !this.targetIpAddress().trim());
  // readonly localIpInvalid = computed(() => this.launchAttempted() && !this.localIpAddress().trim());

  onLaunchClick(): void {
    this.launchAttempted.set(true);
    if (!this.value() || !this.targetIpAddress().trim() /* || !this.localIpAddress().trim() */) {
      return;
    }
    this.launchApplication.emit();
  }
}
