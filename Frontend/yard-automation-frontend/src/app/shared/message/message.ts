import { Component } from '@angular/core';
import { MessageModule } from 'primeng/message';

@Component({
  selector: 'app-message',
  imports: [MessageModule],
  templateUrl: './message.html',
  styleUrl: './message.scss',
})
export class Message {}
