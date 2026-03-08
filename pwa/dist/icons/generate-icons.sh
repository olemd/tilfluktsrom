#!/bin/bash
# Generate simple SVG-based placeholder icons for the PWA
# These should be replaced with proper icons later

for size in 192 512; do
  cat > "public/icons/icon-${size}.svg" << EOF
<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <rect width="${size}" height="${size}" fill="#1A1A2E" rx="32"/>
  <path d="M${size/2} ${size*15/100}L${size*15/100} ${size*55/100}h${size*15/100}v${size*30/100}h${size*40/100}v-${size*30/100}h${size*15/100}Z" fill="#FF6B35" stroke="#fff" stroke-width="${size/50}"/>
  <text x="${size/2}" y="${size*75/100}" text-anchor="middle" fill="#fff" font-size="${size*15/100}" font-weight="bold" font-family="sans-serif">T</text>
</svg>
EOF
done
