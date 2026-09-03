import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { MessageService } from 'primeng/api';
import { FrauscherPrimeOneTheme } from '@inv/frauscher-primeng-theme';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    MessageService,
    providePrimeNG({
      theme: {
        preset: FrauscherPrimeOneTheme,
        options: {
          darkModeSelector: `.dark-mode`,
          cssLayer: {
            name: "primeng",
            order: "application, theme, base, primeng",
          },
        },
      },
    }),
  ]
};
