/**
 * Status bar: status text + refresh button.
 */

/** Update the status text. */
export function setStatus(text: string): void {
  const el = document.getElementById('status-text');
  if (el) el.textContent = text;
}

/** Set the refresh button click handler. */
export function onRefreshClick(handler: () => void): void {
  const btn = document.getElementById('refresh-btn');
  if (btn) btn.addEventListener('click', handler);
}
