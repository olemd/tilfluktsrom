/// <reference types="vite/client" />

// Build-time cache-breaker injected by vite.config.ts
declare const __BUILD_REVISION__: string;

// Asset imports
declare module '*.png' {
  const src: string;
  export default src;
}

declare module '*.svg' {
  const src: string;
  export default src;
}
