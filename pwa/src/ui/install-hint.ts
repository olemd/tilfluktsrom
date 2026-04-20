/**
 * iOS-only "Add to Home Screen" hint.
 *
 * Chrome-based browsers fire `beforeinstallprompt` and we could show a native
 * install UI there; iOS Safari does not. For iOS users the only way to install
 * is Share → Add to Home Screen, so we show a dismissable textual hint the
 * first time they visit in a non-standalone context.
 *
 * Shown once per device (localStorage). Harmless if the heuristic mis-fires
 * on a new iOS version — the hint is dismissable.
 */

import { t } from '../i18n/i18n';

const DISMISSED_KEY = 'tilfluktsrom:ios-install-hint:dismissed';

function isIOS(): boolean {
  // Safari on iPadOS 13+ reports as MacIntel, so also check for touch + Safari.
  const ua = navigator.userAgent;
  if (/iPad|iPhone|iPod/.test(ua)) return true;
  return (
    navigator.maxTouchPoints > 1 &&
    /Macintosh/.test(ua) &&
    /Safari/.test(ua) &&
    !/Chrome|CriOS|FxiOS/.test(ua)
  );
}

function isStandalone(): boolean {
  // iOS-specific property
  const nav = navigator as Navigator & { standalone?: boolean };
  if (nav.standalone) return true;
  // Standards-based check used by Chrome/Edge
  return window.matchMedia?.('(display-mode: standalone)').matches ?? false;
}

export function maybeShow(): void {
  if (!isIOS() || isStandalone()) return;
  if (localStorage.getItem(DISMISSED_KEY) === '1') return;

  const banner = document.createElement('div');
  banner.id = 'ios-install-hint';
  banner.setAttribute('role', 'status');

  const text = document.createElement('span');
  text.textContent = t('ios_install_hint');

  const dismiss = document.createElement('button');
  dismiss.type = 'button';
  dismiss.textContent = t('action_close');
  dismiss.setAttribute('aria-label', t('action_close'));
  dismiss.addEventListener('click', () => {
    localStorage.setItem(DISMISSED_KEY, '1');
    banner.remove();
  });

  banner.append(text, dismiss);
  document.body.appendChild(banner);
}
