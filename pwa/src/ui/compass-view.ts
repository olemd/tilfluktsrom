/**
 * Canvas-based direction arrow pointing toward the selected shelter.
 * Ported from DirectionArrowView.kt — same 7-point arrow polygon.
 *
 * Arrow rotation = shelterBearing - deviceHeading
 *
 * Also draws a discrete north indicator on the perimeter so users can
 * validate compass calibration against a known direction.
 */

const ARROW_COLOR = '#FF6B35';
const OUTLINE_COLOR = '#FFFFFF';
const OUTLINE_WIDTH = 4;
const NORTH_COLOR = 'rgba(207, 216, 220, 0.6)'; // text_secondary at ~60%

let canvas: HTMLCanvasElement | null = null;
let ctx: CanvasRenderingContext2D | null = null;
let currentAngle = 0;
let northAngle: number | null = null;
let animFrameId = 0;

/** Initialize the compass canvas inside the given container element. */
export function initCompass(container: HTMLElement): void {
  canvas = document.createElement('canvas');
  canvas.id = 'compass-canvas';
  canvas.style.width = '100%';
  canvas.style.height = '100%';
  canvas.setAttribute('aria-hidden', 'true');
  container.prepend(canvas);
  resizeCanvas();
  window.addEventListener('resize', resizeCanvas);
}

function resizeCanvas(): void {
  if (!canvas) return;
  const rect = canvas.parentElement!.getBoundingClientRect();
  canvas.width = rect.width * devicePixelRatio;
  canvas.height = rect.height * devicePixelRatio;
  draw();
}

/**
 * Set the arrow direction in degrees.
 * 0 = pointing up, positive = clockwise.
 */
export function setDirection(degrees: number): void {
  currentAngle = degrees;
  if (animFrameId) cancelAnimationFrame(animFrameId);
  animFrameId = requestAnimationFrame(draw);
}

/**
 * Set the angle to north in the view's coordinate space.
 * Typically -deviceHeading. Set to null to hide.
 */
export function setNorthAngle(degrees: number): void {
  northAngle = degrees;
}

function draw(): void {
  if (!canvas) return;
  ctx = canvas.getContext('2d');
  if (!ctx) return;

  const w = canvas.width;
  const h = canvas.height;
  const cx = w / 2;
  const cy = h / 2;
  const size = Math.min(w, h) * 0.4;

  ctx.clearRect(0, 0, w, h);

  // Draw north indicator behind the main arrow
  if (northAngle !== null) {
    drawNorthIndicator(ctx, cx, cy, size);
  }

  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate((currentAngle * Math.PI) / 180);

  // 7-point arrow polygon (same geometry as DirectionArrowView.kt)
  ctx.beginPath();
  ctx.moveTo(0, -size);                         // tip
  ctx.lineTo(size * 0.5, size * 0.3);            // right wing
  ctx.lineTo(size * 0.15, size * 0.1);           // right notch
  ctx.lineTo(size * 0.15, size * 0.7);           // right tail
  ctx.lineTo(-size * 0.15, size * 0.7);          // left tail
  ctx.lineTo(-size * 0.15, size * 0.1);          // left notch
  ctx.lineTo(-size * 0.5, size * 0.3);           // left wing
  ctx.closePath();

  ctx.fillStyle = ARROW_COLOR;
  ctx.fill();
  ctx.strokeStyle = OUTLINE_COLOR;
  ctx.lineWidth = OUTLINE_WIDTH;
  ctx.stroke();

  ctx.restore();
}

/** Small triangle and "N" label on the perimeter, pointing inward. */
function drawNorthIndicator(
  c: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  arrowSize: number,
): void {
  if (northAngle === null) return;

  const radius = arrowSize * 1.35;
  const tickSize = arrowSize * 0.1;
  const rad = (northAngle * Math.PI) / 180;

  c.save();
  c.translate(cx, cy);
  c.rotate(rad);

  // Small triangle
  c.beginPath();
  c.moveTo(0, -radius);
  c.lineTo(-tickSize, -radius - tickSize * 1.8);
  c.lineTo(tickSize, -radius - tickSize * 1.8);
  c.closePath();
  c.fillStyle = NORTH_COLOR;
  c.fill();

  // "N" label
  c.font = `${arrowSize * 0.18}px -apple-system, sans-serif`;
  c.fillStyle = NORTH_COLOR;
  c.textAlign = 'center';
  c.textBaseline = 'bottom';
  c.fillText('N', 0, -radius - tickSize * 2.2);

  c.restore();
}

/** Clean up compass resources. */
export function destroyCompass(): void {
  window.removeEventListener('resize', resizeCanvas);
  if (animFrameId) cancelAnimationFrame(animFrameId);
  canvas?.remove();
  canvas = null;
  ctx = null;
}
