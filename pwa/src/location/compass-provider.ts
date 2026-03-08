/**
 * Compass heading provider using DeviceOrientationEvent.
 *
 * Handles platform differences:
 * - iOS Safari: requires requestPermission(), uses webkitCompassHeading
 * - Android Chrome: uses deviceorientationabsolute, heading = (360 - alpha)
 *
 * Applies a low-pass filter for smooth rotation.
 */

export type HeadingCallback = (heading: number) => void;

const SMOOTHING = 0.3; // Low-pass filter coefficient (0..1, lower = smoother)

let currentHeading = 0;
let callback: HeadingCallback | null = null;
let listening = false;

function handleOrientation(event: DeviceOrientationEvent): void {
  let heading: number | null = null;

  // iOS Safari provides webkitCompassHeading directly
  if ('webkitCompassHeading' in event) {
    heading = (event as DeviceOrientationEvent & { webkitCompassHeading: number })
      .webkitCompassHeading;
  } else if (event.alpha != null) {
    // Android: alpha is counterclockwise from north for absolute orientation
    heading = (360 - event.alpha) % 360;
  }

  if (heading == null || callback == null) return;

  // Low-pass filter for smooth rotation
  // Handle wraparound at 0/360 boundary
  let delta = heading - currentHeading;
  if (delta > 180) delta -= 360;
  if (delta < -180) delta += 360;
  currentHeading = (currentHeading + delta * SMOOTHING + 360) % 360;

  callback(currentHeading);
}

/**
 * Request compass permission (required on iOS 13+).
 * Must be called from a user gesture handler.
 * Returns true if permission was granted or not needed.
 */
export async function requestPermission(): Promise<boolean> {
  const DOE = DeviceOrientationEvent as unknown as {
    requestPermission?: () => Promise<string>;
  };

  if (typeof DOE.requestPermission === 'function') {
    try {
      const result = await DOE.requestPermission();
      return result === 'granted';
    } catch {
      return false;
    }
  }

  // Permission not required (Android, desktop)
  return true;
}

/** Start listening for compass heading changes. */
export function startCompass(onHeading: HeadingCallback): void {
  callback = onHeading;
  if (listening) return;
  listening = true;

  // Prefer absolute orientation (Android), fall back to standard
  const eventName = 'ondeviceorientationabsolute' in window
    ? 'deviceorientationabsolute'
    : 'deviceorientation';
  window.addEventListener(eventName as 'deviceorientation', handleOrientation);
}

/** Stop listening for compass updates. */
export function stopCompass(): void {
  listening = false;
  callback = null;
  window.removeEventListener(
    'deviceorientationabsolute' as unknown as 'deviceorientation',
    handleOrientation,
  );
  window.removeEventListener('deviceorientation', handleOrientation);
}
