/**
 * Hexagonal grid math, anchored at (lat=0, lon=0).
 *
 * Flat-top hexes with side length = HEX_SIZE_METERS.
 * (q, r) are axial coordinates — stable globally so each integer pair
 * always maps to the same patch of the planet.
 *
 * Conversion path:
 *   (lat, lon) → local meters (x = lon·111320·cos(lat),  y = lat·111320)
 *               → (qFloat, rFloat) via flat-top inverse
 *               → integer (q, r) via cube-rounding
 *
 * For rendering, each (q, r) is converted back to the 6 corner lat/lon
 * pairs that React Native Maps' Polygon expects.
 */

const METERS_PER_DEG_LAT = 111320;
const SQRT3 = Math.sqrt(3);

export const HEX_SIZE_METERS = 28; // edge length; hex covers ~2,036 m²

const SIZE = HEX_SIZE_METERS;

function latLonToMeters(lat, lon) {
  const y = lat * METERS_PER_DEG_LAT;
  const x = lon * METERS_PER_DEG_LAT * Math.cos((lat * Math.PI) / 180);
  return { x, y };
}

function metersToLatLon(x, y) {
  const lat = y / METERS_PER_DEG_LAT;
  const lon = x / (METERS_PER_DEG_LAT * Math.cos((lat * Math.PI) / 180));
  return { latitude: lat, longitude: lon };
}

function hexRound(qF, rF) {
  const sF = -qF - rF;
  let q = Math.round(qF);
  let r = Math.round(rF);
  let s = Math.round(sF);
  const dq = Math.abs(q - qF);
  const dr = Math.abs(r - rF);
  const ds = Math.abs(s - sF);
  if (dq > dr && dq > ds) q = -r - s;
  else if (dr > ds) r = -q - s;
  return { q, r };
}

export function latLonToHex(lat, lon) {
  const { x, y } = latLonToMeters(lat, lon);
  const qF = ((2 / 3) * x) / SIZE;
  const rF = ((-1 / 3) * x + (SQRT3 / 3) * y) / SIZE;
  return hexRound(qF, rF);
}

function hexCenterMeters(q, r) {
  const x = SIZE * 1.5 * q;
  const y = SIZE * SQRT3 * (r + q / 2);
  return { x, y };
}

export function hexCorners(q, r) {
  const c = hexCenterMeters(q, r);
  const corners = [];
  for (let i = 0; i < 6; i++) {
    const angle = (Math.PI / 3) * i; // flat-top: 0,60,120,180,240,300
    const cx = c.x + SIZE * Math.cos(angle);
    const cy = c.y + SIZE * Math.sin(angle);
    corners.push(metersToLatLon(cx, cy));
  }
  return corners;
}

export function hexKey(q, r) {
  return `${q}|${r}`;
}

/**
 * Return the (q,r) keys for the player's hex plus a ring of `radius`
 * hexes around it. Used to render only the visible neighbourhood.
 */
export function neighbourhood(centerQ, centerR, radius = 6) {
  const cells = [];
  for (let dq = -radius; dq <= radius; dq++) {
    for (let dr = Math.max(-radius, -dq - radius); dr <= Math.min(radius, -dq + radius); dr++) {
      cells.push({ q: centerQ + dq, r: centerR + dr });
    }
  }
  return cells;
}

/** Surface area of one flat-top hex with side `s` is (3√3 / 2)·s². */
export const HEX_AREA_M2 = (3 * SQRT3 / 2) * SIZE * SIZE;
