import { describe, it, expect } from 'vitest';
import {
  distanceMeters,
  bearingDegrees,
  formatDistance,
} from '../src/util/distance-utils';

describe('distanceMeters', () => {
  it('returns 0 for same point', () => {
    expect(distanceMeters(59.9, 10.7, 59.9, 10.7)).toBe(0);
  });

  it('calculates Oslo to Bergen distance (~305km)', () => {
    // Oslo: 59.9139, 10.7522 — Bergen: 60.3913, 5.3221
    const d = distanceMeters(59.9139, 10.7522, 60.3913, 5.3221);
    expect(d).toBeGreaterThan(300_000);
    expect(d).toBeLessThan(310_000);
  });

  it('calculates short distance (~1.1km)', () => {
    // ~1km apart in Oslo
    const d = distanceMeters(59.91, 10.75, 59.92, 10.75);
    expect(d).toBeGreaterThan(1000);
    expect(d).toBeLessThan(1200);
  });
});

describe('bearingDegrees', () => {
  it('north bearing is ~0', () => {
    const b = bearingDegrees(59.9, 10.7, 60.9, 10.7);
    expect(b).toBeCloseTo(0, 0);
  });

  it('east bearing is ~90', () => {
    const b = bearingDegrees(59.9, 10.0, 59.9, 11.0);
    expect(b).toBeGreaterThan(85);
    expect(b).toBeLessThan(95);
  });

  it('south bearing is ~180', () => {
    const b = bearingDegrees(60.0, 10.7, 59.0, 10.7);
    expect(b).toBeCloseTo(180, 0);
  });

  it('west bearing is ~270', () => {
    const b = bearingDegrees(59.9, 11.0, 59.9, 10.0);
    expect(b).toBeGreaterThan(265);
    expect(b).toBeLessThan(275);
  });
});

describe('formatDistance', () => {
  it('formats short distance in meters', () => {
    expect(formatDistance(42)).toBe('42 m');
    expect(formatDistance(999)).toBe('999 m');
  });

  it('formats long distance in km', () => {
    expect(formatDistance(1000)).toBe('1.0 km');
    expect(formatDistance(1500)).toBe('1.5 km');
    expect(formatDistance(12345)).toBe('12.3 km');
  });

  it('rounds meters to nearest integer', () => {
    expect(formatDistance(42.7)).toBe('43 m');
  });
});
