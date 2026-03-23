import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  define: {
    // Injected as a global — changes every build, breaking any stale cache
    __BUILD_REVISION__: JSON.stringify(
      `${new Date().toISOString().slice(0, 16)}`,
    ),
  },
  plugins: [
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        // Precache all built assets (JS/CSS get content hashes from Vite;
        // files in public/ like shelters.json get a Workbox revision hash
        // computed from their content, so any change triggers re-fetch).
        globPatterns: ['**/*.{js,css,html,json,png,svg,ico,webmanifest}'],

        // Remove old precache entries from previous builds
        cleanupOutdatedCaches: true,

        // SPA: serve index.html for all navigation requests
        navigateFallback: '/index.html',

        // Vite already hashes JS/CSS filenames — skip Workbox's
        // cache-bust query parameter for those
        dontCacheBustURLsMatching: /\.[0-9a-f]{8}\./,

        // Runtime caching for map tiles (not precached — cached as viewed)
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/[abc]\.tile\.openstreetmap\.org\/.*/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'osm-tiles',
              expiration: {
                maxEntries: 5000,
                maxAgeSeconds: 30 * 24 * 60 * 60, // 30 days
              },
              cacheableResponse: {
                statuses: [200],
              },
            },
          },
        ],
      },
      manifest: false, // We provide our own manifest.webmanifest
    }),
  ],
  resolve: {
    alias: {
      '@': '/src',
    },
  },
});
