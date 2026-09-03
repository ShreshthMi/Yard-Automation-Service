# Yard Automation Frontend

Angular web client for the Yard Automation system. It connects to the backend over REST and WebSocket to display live track status, warnings, camera feed, and activity logs for yard operations.

Built with Angular 21 and [PrimeNG](https://primeng.org/) (Frauscher theme).

## Prerequisites

- Node.js compatible with Angular 21 and npm `10.9.3` (see `packageManager` in `package.json`)
- Access to the Frauscher internal npm registries configured in `.npmrc`:
  - `https://at-nexus01.frauscher.host/repository/npm-proxy/`
  - `https://gitlab.frauscher.app/api/v4/projects/1559/packages/npm/` (scope `@inv`, used for `@inv/frauscher-primeng-theme`)

## Getting started

Install dependencies:

```bash
npm install
```

Start the dev server:

```bash
npm start
```

Then open `http://localhost:4200/`. The app reloads automatically on source changes.

## Configuration

Runtime API/WebSocket endpoints are set in `src/app/environments/environment.ts`:

```ts
export const environment: Environment = {
  production: false,
  apiUrl: 'https://localhost:5001/api',
  wsUrl: 'wss://localhost:5001/ws',
};
```

Update `apiUrl` / `wsUrl` to point at your backend before running against a non-local environment.

## Project structure

```
src/app/
  components/   # Feature components (header, layout, track, warning, yard-camera, ...)
  shared/       # Reusable UI building blocks (card, toast, loader, select, ...)
  services/     # WebSocket connection, audio, activity log, toast services
  stores/       # App state (websocket-connection, initial-connection)
  environments/ # Environment configuration
```

## Available scripts

| Command | Description |
| --- | --- |
| `npm start` | Run the dev server (`ng serve`) |
| `npm run build` | Production build, output to `dist/` |
| `npm run watch` | Development build in watch mode |
| `npm test` | Run unit tests with Vitest |

## Code scaffolding

```bash
ng generate component component-name
```

See all available schematics with:

```bash
ng generate --help
```

## Additional resources

- [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli)
- [PrimeNG documentation](https://primeng.org/)
