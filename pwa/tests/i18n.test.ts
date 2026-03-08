import { describe, it, expect, beforeEach, vi } from 'vitest';

// Must mock navigator.languages before importing i18n
const mockLanguages = { value: ['en'] };
Object.defineProperty(globalThis.navigator, 'languages', {
  get: () => mockLanguages.value,
  configurable: true,
});

describe('i18n', () => {
  beforeEach(async () => {
    // Re-import to reset state
    vi.resetModules();
  });

  it('returns English strings by default', async () => {
    mockLanguages.value = ['en'];
    const { initLocale, t } = await import('../src/i18n/i18n');
    initLocale();
    expect(t('app_name')).toBe('Tilfluktsrom');
    expect(t('status_ready')).toBe('Ready');
  });

  it('detects Norwegian Bokmål', async () => {
    mockLanguages.value = ['nb-NO'];
    const { initLocale, t } = await import('../src/i18n/i18n');
    initLocale();
    expect(t('status_ready')).toBe('Klar');
  });

  it('detects "no" as Bokmål', async () => {
    mockLanguages.value = ['no'];
    const { initLocale, t } = await import('../src/i18n/i18n');
    initLocale();
    expect(t('status_ready')).toBe('Klar');
  });

  it('detects Nynorsk', async () => {
    mockLanguages.value = ['nn'];
    const { initLocale, t } = await import('../src/i18n/i18n');
    initLocale();
    expect(t('status_offline')).toBe('Fråkopla modus');
  });

  it('substitutes %d placeholders', async () => {
    mockLanguages.value = ['en'];
    const { initLocale, t } = await import('../src/i18n/i18n');
    initLocale();
    expect(t('status_shelters_loaded', 556)).toBe('556 shelters loaded');
    expect(t('shelter_capacity', 200)).toBe('200 places');
  });

  it('falls back to English for unknown locale', async () => {
    mockLanguages.value = ['fr'];
    const { initLocale, t } = await import('../src/i18n/i18n');
    initLocale();
    expect(t('status_ready')).toBe('Ready');
  });

  it('returns key for unknown string key', async () => {
    const { t } = await import('../src/i18n/i18n');
    expect(t('nonexistent_key')).toBe('nonexistent_key');
  });
});
