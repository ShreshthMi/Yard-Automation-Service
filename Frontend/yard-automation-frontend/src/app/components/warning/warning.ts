import { Component, input } from '@angular/core';

@Component({
  selector: 'app-warning',
  imports: [],
  templateUrl: './warning.html',
  styleUrl: './warning.scss',
})
export class Warning {
  readonly speedLimit = input<number>(20);
}
