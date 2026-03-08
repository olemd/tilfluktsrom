/**
 * Minimal i18n system: detects locale from navigator.languages,
 * provides t(key, ...args) for string lookup with %d/%s substitution.
 */

import { en } from './en';
import { nb } from './nb';
import { nn } from './nn';

const locales: Record<string, Record<string, string>> = { en, nb, nn };

let currentLocale = 'en';

/** Detect and set locale from browser preferences. */
export function initLocale(): void {
  const langs = navigator.languages ?? [navigator.language];
  for (const lang of langs) {
    const code = lang.toLowerCase().split('-')[0];
    if (code in locales) {
      currentLocale = code;
      return;
    }
    // nb and nn both start with "n" — also match "no" as Bokmål
    if (code === 'no') {
      currentLocale = 'nb';
      return;
    }
  }
}

/** Get current locale code. */
export function getLocale(): string {
  return currentLocale;
}

/**
 * Translate a string key, substituting %d and %s with provided arguments.
 * Falls back to English, then to the raw key.
 */
export function t(key: string, ...args: (string | number)[]): string {
  let str = locales[currentLocale]?.[key] ?? locales.en[key] ?? key;

  // Replace %d and %s placeholders sequentially
  for (const arg of args) {
    str = str.replace(/%[ds]/, String(arg));
  }

  return str;
}
