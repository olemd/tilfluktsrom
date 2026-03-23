/**
 * About dialog: app info, privacy statement, data sources, copyright.
 * Includes a "Clear cached data" button that removes all local storage.
 * Opens as a modal overlay, same pattern as loading-overlay.
 */

import { t } from '../i18n/i18n';

let overlay: HTMLDivElement | null = null;
let previousFocus: HTMLElement | null = null;

/** Show the about dialog. */
export function showAbout(): void {
  if (overlay) return;

  previousFocus = document.activeElement as HTMLElement | null;

  overlay = document.createElement('div');
  overlay.id = 'about-overlay';
  overlay.setAttribute('role', 'dialog');
  overlay.setAttribute('aria-modal', 'true');
  overlay.setAttribute('aria-label', t('about_title'));

  const content = document.createElement('div');
  content.className = 'about-content';

  content.appendChild(heading(t('about_title')));
  content.appendChild(para(t('about_description')));

  content.appendChild(subheading(t('about_privacy_title')));
  content.appendChild(para(t('about_privacy_body')));

  content.appendChild(subheading(t('about_data_title')));
  content.appendChild(para(t('about_data_body')));

  content.appendChild(subheading(t('about_stored_title')));
  content.appendChild(para(t('about_stored_body')));

  // Clear cache button
  const clearBtn = document.createElement('button');
  clearBtn.className = 'about-clear-btn';
  clearBtn.textContent = t('action_clear_cache');
  clearBtn.addEventListener('click', () => clearAllData(clearBtn));
  content.appendChild(clearBtn);

  const footer = document.createElement('div');
  footer.className = 'about-footer';
  footer.appendChild(small(t('about_copyright')));
  footer.appendChild(small(t('about_open_source')));
  content.appendChild(footer);

  const closeBtn = document.createElement('button');
  closeBtn.className = 'about-close-btn';
  closeBtn.textContent = t('action_close');
  closeBtn.addEventListener('click', hideAbout);
  content.appendChild(closeBtn);

  overlay.appendChild(content);
  document.body.appendChild(overlay);

  closeBtn.focus();
}

/** Hide the about dialog and restore focus. */
export function hideAbout(): void {
  if (overlay) {
    overlay.remove();
    overlay = null;
  }
  previousFocus?.focus();
  previousFocus = null;
}

/** Clear all cached data: IndexedDB, localStorage, and service worker caches. */
async function clearAllData(btn: HTMLButtonElement): Promise<void> {
  btn.disabled = true;

  // Clear localStorage (map cache metadata)
  localStorage.clear();

  // Clear IndexedDB (shelter database)
  const dbs = await indexedDB.databases?.() ?? [];
  for (const db of dbs) {
    if (db.name) indexedDB.deleteDatabase(db.name);
  }

  // Clear service worker caches (map tiles, precache)
  const cacheNames = await caches.keys();
  for (const name of cacheNames) {
    await caches.delete(name);
  }

  btn.textContent = t('cache_cleared');
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
  return el;
}
