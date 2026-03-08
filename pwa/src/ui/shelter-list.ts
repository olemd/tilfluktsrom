/**
 * Bottom sheet shelter list component.
 * Renders the 3 nearest shelters with distance, bearing mini-arrow, and address.
 */

import type { ShelterWithDistance } from '../types';
import { formatDistance } from '../util/distance-utils';
import { t } from '../i18n/i18n';

let container: HTMLElement | null = null;
let onSelect: ((index: number) => void) | null = null;
let selectedIndex = 0;

/** Initialize the shelter list inside the given container. */
export function initShelterList(
  el: HTMLElement,
  onShelterSelect: (index: number) => void,
): void {
  container = el;
  onSelect = onShelterSelect;
}

/** Render the list of nearest shelters using safe DOM methods. */
export function updateList(
  shelters: ShelterWithDistance[],
  currentSelectedIndex: number,
): void {
  if (!container) return;
  selectedIndex = currentSelectedIndex;

  // Clear existing items
  while (container.firstChild) {
    container.removeChild(container.firstChild);
  }

  shelters.forEach((swd, i) => {
    const item = document.createElement('button');
    item.className = `shelter-item${i === selectedIndex ? ' selected' : ''}`;

    const addressSpan = document.createElement('span');
    addressSpan.className = 'shelter-item-address';
    addressSpan.textContent = swd.shelter.adresse;

    const detailsSpan = document.createElement('span');
    detailsSpan.className = 'shelter-item-details';
    detailsSpan.textContent = [
      formatDistance(swd.distanceMeters),
      t('shelter_capacity', swd.shelter.plasser),
      t('shelter_room_nr', swd.shelter.romnr),
    ].join(' \u00B7 ');

    item.appendChild(addressSpan);
    item.appendChild(detailsSpan);
    item.addEventListener('click', () => {
      onSelect?.(i);
    });
    container!.appendChild(item);
  });
}
