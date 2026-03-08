/**
 * Entry point: wait for DOM, initialize locale, boot the app.
 *
 * The __BUILD_REVISION__ constant is injected by Vite at build time and
 * changes on every build. Combined with vite-plugin-pwa's autoUpdate
 * registration, this ensures the service worker detects any new deployment
 * and swaps in the fresh precache immediately.
 */

import './styles/main.css';
import 'leaflet/dist/leaflet.css';
import { initLocale } from './i18n/i18n';
import { init } from './app';
import { setStatus } from './ui/status-bar';
import { t } from './i18n/i18n';

console.info(`[tilfluktsrom] build ${__BUILD_REVISION__}`);

document.addEventListener('DOMContentLoaded', async () => {
  initLocale();

  // Request persistent storage (helps prevent iOS eviction)
  if (navigator.storage?.persist) {
    await navigator.storage.persist();
  }

  // Listen for service worker updates — flash a status message when a new
  // version activates so the user knows they have fresh code/data.
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      setStatus(t('update_success'));
    });
  }

  await init();
});
