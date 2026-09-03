import { Component, input } from '@angular/core';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

@Component({
  selector: 'app-loader',
  imports: [ProgressSpinnerModule],
  templateUrl: './loader.html',
  styleUrl: './loader.scss',
})
export class Loader {
  readonly message = input<string>('Connecting to server...');
}
