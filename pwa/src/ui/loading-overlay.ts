/**
 * Loading overlay: spinner + message + OK/Skip buttons.
 * Same flow as Android: prompt before map caching, user can skip.
 *
 * Accessibility: the overlay is a modal dialog (role="dialog", aria-modal).
 * Focus is moved into the dialog when shown and restored when hidden.
 */

import { t } from '../i18n/i18n';

/** Element that had focus before the overlay opened. */
let previousFocus: HTMLElement | null = null;

/** Show the loading overlay with a message and optional spinner. */
export function showLoading(message: string, showSpinner = true): void {
  const overlay = document.getElementById('loading-overlay')!;
  const text = document.getElementById('loading-text')!;
  const spinner = document.getElementById('loading-spinner')!;
  const buttonRow = document.getElementById('loading-button-row')!;

  previousFocus = document.activeElement as HTMLElement | null;
  text.textContent = message;
  overlay.setAttribute('aria-label', message);
  spinner.style.display = showSpinner ? 'block' : 'none';
  buttonRow.style.display = 'none';
  overlay.style.display = 'flex';
  text.focus();
}

/** Show the cache prompt (OK / Skip buttons, no spinner). */
export function showCachePrompt(
  message: string,
  onOk: () => void,
  onSkip: () => void,
): void {
  const overlay = document.getElementById('loading-overlay')!;
  const text = document.getElementById('loading-text')!;
  const spinner = document.getElementById('loading-spinner')!;
  const buttonRow = document.getElementById('loading-button-row')!;
  const okBtn = document.getElementById('loading-ok-btn')!;
  const skipBtn = document.getElementById('loading-skip-btn')!;

  previousFocus = document.activeElement as HTMLElement | null;
  text.textContent = message;
  overlay.setAttribute('aria-label', message);
  spinner.style.display = 'none';
  buttonRow.style.display = 'flex';
  overlay.style.display = 'flex';

  okBtn.textContent = t('action_cache_ok');
  skipBtn.textContent = t('action_skip');

  okBtn.onclick = () => {
    hideLoading();
    onOk();
  };
  skipBtn.onclick = () => {
    hideLoading();
    onSkip();
  };

  okBtn.focus();
}

/** Update loading text (e.g. progress). */
export function updateLoadingText(message: string): void {
  const text = document.getElementById('loading-text');
  if (text) text.textContent = message;
}

/** Hide the loading overlay and restore focus. */
export function hideLoading(): void {
  const overlay = document.getElementById('loading-overlay');
  if (overlay) overlay.style.display = 'none';
  previousFocus?.focus();
  previousFocus = null;
}
