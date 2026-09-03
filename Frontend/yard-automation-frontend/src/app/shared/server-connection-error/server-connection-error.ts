import { Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-server-connection-error',
  imports: [ButtonModule],
  templateUrl: './server-connection-error.html',
  styleUrl: './server-connection-error.scss',
})
export class ServerConnectionError {
  readonly title = input<string>('Server not connected');
  readonly description = input<string>('Connection to the server could not be established!');
  readonly steps = input<string[]>([
    'Ensure you are within the required network coverage.',
    'Check that the device is connected to the correct network.',
    'Verify that the server is available and try again.',
  ]);
  readonly buttonLabel = input<string>('Establish connection');

  readonly establishConnection = output<void>();
}
