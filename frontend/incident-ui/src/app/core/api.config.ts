/**
 * Service endpoints. Each backend service owns its own port, so the UI talks to the service that
 * owns the data rather than routing everything through one of them.
 */
export const API = {
  incidents: 'http://localhost:8081',
  analysis: 'http://localhost:8083',
} as const;
