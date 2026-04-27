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
import { maybeShow as maybeShowIosInstallHint } from './ui/install-hint';

console.info(`[tilfluktsrom] build ${__BUILD_REVISION__}`);

// Make `registerType: 'autoUpdate'` actually auto-update the running tab.
// vite-plugin-pwa's autoUpdate strategy makes the new service worker
// skipWaiting + clientsClaim, but the JS already loaded in the open tab is
// the *old* build until something triggers a navigation. Without this
// listener, a deploy is invisible until the user manually refreshes.
//
// We *defer* the reload until the user next backgrounds the app
// (visibilityState === 'hidden') instead of reloading immediately. This is
// an emergency app: a mid-task reload would lose the selected shelter,
// compass mode, and any in-flight UI state right when the user can least
// afford to be surprised. Deferring keeps the "auto" promise (they're on
// the new version next time they look at the screen) without interrupting
// active use.
//
// The `wasAlreadyControlled` guard avoids reloading on the very first SW
// install (when there was no previous controller — that's a fresh visit,
// not an update).
if ('serviceWorker' in navigator) {
  const wasAlreadyControlled = !!navigator.serviceWorker.controller;
  let pendingReload = false;

  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!wasAlreadyControlled) return;
    pendingReload = true;
  });

  document.addEventListener('visibilitychange', () => {
    if (pendingReload && document.visibilityState === 'hidden') {
      pendingReload = false;
      window.location.reload();
    }
  });
}

document.addEventListener('DOMContentLoaded', async () => {
  initLocale();

  // Request persistent storage (helps prevent iOS eviction)
  if (navigator.storage?.persist) {
    await navigator.storage.persist();
  }

  await init();

  // Shown only on first iOS Safari visit, once per device. Placed after init()
  // so the banner doesn't compete with the loading overlay.
  maybeShowIosInstallHint();
});
