import { Component, model, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SelectModule } from 'primeng/select';
import { FloatLabelModule } from 'primeng/floatlabel';

@Component({
  selector: 'app-select',
  imports: [SelectModule, FloatLabelModule, FormsModule],
  templateUrl: './select.html',
  styleUrl: './select.scss',
})
export class Select {
  readonly options = input<any[]>([]);
  readonly optionLabel = input<string>('name');
  readonly optionValue = input<string | undefined>(undefined);
  readonly label = input<string>('');
  readonly placeholder = input<string>('');
  readonly inputId = input<string>('app-select');

  readonly value = model<any>(undefined);
}
