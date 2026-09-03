import { Component } from '@angular/core';
import { ToastModule } from 'primeng/toast';

@Component({
  selector: 'app-toast',
  imports: [ToastModule],
  templateUrl: './toast.html',
  styleUrl: './toast.scss',
})
export class Toast {}
