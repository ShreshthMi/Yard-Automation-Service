export interface Environment {
  production: boolean;
  apiUrl: string;
  wsUrl: string;
}

export const environment: Environment = {
  production: false,
  apiUrl: 'https://localhost:5001/api',
  wsUrl: 'wss://localhost:5001/ws',
};
