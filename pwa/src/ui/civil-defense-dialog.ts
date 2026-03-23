/**
 * Civil defense info dialog: what to do when the alarm sounds.
 * Same content as the Android CivilDefenseInfoDialog.
 * Links to the about dialog at the bottom.
 */

import { t } from '../i18n/i18n';
import { showAbout } from './about-dialog';

let overlay: HTMLDivElement | null = null;
let previousFocus: HTMLElement | null = null;

/** Show the civil defense info dialog. */
export function showCivilDefenseInfo(): void {
  if (overlay) return;

  previousFocus = document.activeElement as HTMLElement | null;

  overlay = document.createElement('div');
  overlay.id = 'civil-defense-overlay';
  overlay.setAttribute('role', 'dialog');
  overlay.setAttribute('aria-modal', 'true');
  overlay.setAttribute('aria-label', t('civil_defense_title'));

  const content = document.createElement('div');
  content.className = 'about-content';

  content.appendChild(heading(t('civil_defense_title')));

  for (let i = 1; i <= 5; i++) {
    content.appendChild(subheading(t(`civil_defense_step${i}_title`)));
    content.appendChild(para(t(`civil_defense_step${i}_body`)));
  }

  content.appendChild(small(t('civil_defense_source')));

  // "About this app" link
  const aboutLink = document.createElement('button');
  aboutLink.className = 'about-link-btn';
  aboutLink.textContent = t('action_about');
  aboutLink.addEventListener('click', () => {
    hideCivilDefenseInfo();
    showAbout();
  });
  content.appendChild(aboutLink);

  const closeBtn = document.createElement('button');
  closeBtn.className = 'about-close-btn';
  closeBtn.textContent = t('action_close');
  closeBtn.addEventListener('click', hideCivilDefenseInfo);
  content.appendChild(closeBtn);

  overlay.appendChild(content);
  document.body.appendChild(overlay);

  closeBtn.focus();
}

/** Hide the dialog and restore focus. */
export function hideCivilDefenseInfo(): void {
  if (overlay) {
    overlay.remove();
    overlay = null;
  }
  previousFocus?.focus();
  previousFocus = null;
}

function heading(text: string): HTMLElement {
  const el = document.createElement('h2');
  el.textContent = text;
  el.className = 'about-heading';
  return el;
}

function subheading(text: string): HTMLElement {
  const el = document.createElement('h3');
  el.textContent = text;
  el.className = 'about-subheading';
  return el;
}

function para(text: string): HTMLElement {
  const el = document.createElement('p');
  el.textContent = text;
  el.className = 'about-para';
  return el;
}

function small(text: string): HTMLElement {
  const el = document.createElement('p');
  el.textContent = text;
  el.className = 'about-small';
  el.style.fontStyle = 'italic';
  return el;
}
