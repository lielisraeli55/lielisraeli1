import { HEX_AREA_M2 } from './grid';

/** Per-cell capture base. Take-overs are worth more (riskier). */
export const POINTS_FRESH = 10;
export const POINTS_TAKEOVER = 25;

/**
 * Sum the scoreboard for a given player from a `cells` map.
 * cells: { "q|r": { owner, color, capturedAt, score } }
 */
export function scoreboard(cells) {
  const map = {};
  Object.values(cells).forEach((c) => {
    if (!c?.owner) return;
    const slot = map[c.owner] || { owner: c.owner, color: c.color, cells: 0, points: 0 };
    slot.cells += 1;
    slot.points += c.score ?? POINTS_FRESH;
    slot.color = c.color;
    map[c.owner] = slot;
  });
  return Object.values(map)
    .map((s) => ({
      ...s,
      areaM2: s.cells * HEX_AREA_M2,
      areaKm2: (s.cells * HEX_AREA_M2) / 1e6,
    }))
    .sort((a, b) => b.points - a.points);
}

export function formatArea(km2) {
  if (km2 >= 1) return `${km2.toFixed(2)} km²`;
  if (km2 >= 0.01) return `${(km2 * 1000).toFixed(0)} ㎡ × 1000`;
  return `${(km2 * 1e6).toFixed(0)} m²`;
}
