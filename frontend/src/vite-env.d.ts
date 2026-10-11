/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Absolute API base URL injected by Vercel, e.g.
   * https://api.pkampus.me/api/v1.
   * Empty in local dev — client.ts falls back to '/api/v1' (Vite proxy).
   */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
