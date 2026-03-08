import { describe, it, expect } from 'vitest';
import { findNearest } from '../src/location/shelter-finder';
import type { Shelter } from '../src/types';

const shelters: Shelter[] = [
  {
    lokalId: 'a',
    romnr: 1,
    plasser: 100,
    adresse: 'Near',
    latitude: 59.91,
    longitude: 10.75,
  },
  {
    lokalId: 'b',
    romnr: 2,
    plasser: 200,
    adresse: 'Mid',
    latitude: 59.95,
    longitude: 10.75,
  },
  {
    lokalId: 'c',
    romnr: 3,
    plasser: 300,
    adresse: 'Far',
    latitude: 60.0,
    longitude: 10.75,
  },
  {
    lokalId: 'd',
    romnr: 4,
    plasser: 400,
    adresse: 'Very Far',
    latitude: 61.0,
    longitude: 10.75,
  },
];

describe('findNearest', () => {
  it('returns shelters sorted by distance', () => {
    const result = findNearest(shelters, 59.9, 10.75);
    expect(result).toHaveLength(3);
    expect(result[0].shelter.lokalId).toBe('a');
    expect(result[1].shelter.lokalId).toBe('b');
    expect(result[2].shelter.lokalId).toBe('c');
  });

  it('returns requested count', () => {
    const result = findNearest(shelters, 59.9, 10.75, 2);
    expect(result).toHaveLength(2);
  });

  it('returns all if fewer than count', () => {
    const result = findNearest(shelters.slice(0, 1), 59.9, 10.75, 5);
    expect(result).toHaveLength(1);
  });

  it('calculates distance and bearing', () => {
    const result = findNearest(shelters, 59.9, 10.75, 1);
    expect(result[0].distanceMeters).toBeGreaterThan(0);
    expect(result[0].bearingDegrees).toBeGreaterThanOrEqual(0);
    expect(result[0].bearingDegrees).toBeLessThan(360);
  });

  it('handles empty shelter list', () => {
    const result = findNearest([], 59.9, 10.75);
    expect(result).toHaveLength(0);
  });
});
