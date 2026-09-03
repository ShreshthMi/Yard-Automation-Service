import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {Header} from './components/header/header';
import {Layout} from './components/layout/layout';
import {Toast} from './shared/toast/toast';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, Layout, Toast],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly title = signal('yard-automation-frontend');
}
