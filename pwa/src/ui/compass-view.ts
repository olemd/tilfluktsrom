/**
 * Canvas-based direction arrow pointing toward the selected shelter.
 * Ported from DirectionArrowView.kt — same 7-point arrow polygon.
 *
 * Arrow rotation = shelterBearing - deviceHeading
 */

const ARROW_COLOR = '#FF6B35';
const OUTLINE_COLOR = '#FFFFFF';
const OUTLINE_WIDTH = 4;

let canvas: HTMLCanvasElement | null = null;
let ctx: CanvasRenderingContext2D | null = null;
let currentAngle = 0;
let animFrameId = 0;

/** Initialize the compass canvas inside the given container element. */
export function initCompass(container: HTMLElement): void {
  canvas = document.createElement('canvas');
  canvas.id = 'compass-canvas';
  canvas.style.width = '100%';
  canvas.style.height = '100%';
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

/** Clean up compass resources. */
export function destroyCompass(): void {
  window.removeEventListener('resize', resizeCanvas);
  if (animFrameId) cancelAnimationFrame(animFrameId);
  canvas?.remove();
  canvas = null;
  ctx = null;
}
