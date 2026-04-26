/**
 * Lightweight anti-cheat for territory captures.
 *
 *   1. GPS accuracy: must be ≤ MAX_ACCURACY_M before capturing.
 *   2. Implausible speed: if the new fix implies > MAX_SPEED_MPS since
 *      the last fix, mark this fix as suspicious. Several in a row
 *      means the player is teleporting / spoofing.
 *   3. Dwell time: must stand inside the cell for MIN_DWELL_MS before
 *      it can be claimed. Stops drive-bys and edge-of-cell exploits.
 */

export const MAX_ACCURACY_M = 20;
export const MAX_SPEED_MPS = 30;       // ~108 km/h (highway)
export const MIN_DWELL_MS = 1500;
export const SPOOF_STRIKES = 3;        // suspicious fixes before locking out
export const SPOOF_LOCKOUT_MS = 15000; // 15 s lock once tripped

function haversineMeters(a, b) {
  const R = 6371000;
  const dLat = ((b.latitude - a.latitude) * Math.PI) / 180;
  const dLon = ((b.longitude - a.longitude) * Math.PI) / 180;
  const lat1 = (a.latitude * Math.PI) / 180;
  const lat2 = (b.latitude * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

export class AntiCheat {
  constructor() {
    this.lastFix = null;
    this.cellEnteredAt = null;
    this.lastCellKey = null;
    this.spoofStrikes = 0;
    this.lockedUntil = 0;
  }

  /** Feed every location update here. Returns { ok, reason }. */
  acceptFix(coords, accuracy, ts) {
    const now = ts || Date.now();
    if (this.lastFix) {
      const dtSec = Math.max(0.001, (now - this.lastFix.ts) / 1000);
      const dist = haversineMeters(coords, this.lastFix.coords);
      const speed = dist / dtSec;
      if (speed > MAX_SPEED_MPS && dist > 30) {
        this.spoofStrikes++;
        if (this.spoofStrikes >= SPOOF_STRIKES) {
          this.lockedUntil = now + SPOOF_LOCKOUT_MS;
        }
      } else {
        this.spoofStrikes = Math.max(0, this.spoofStrikes - 1);
      }
    }
    this.lastFix = { coords, ts: now, accuracy };
    return {
      ok: now >= this.lockedUntil && (accuracy ?? Infinity) <= MAX_ACCURACY_M,
      reason: now < this.lockedUntil
        ? 'lockout'
        : (accuracy ?? Infinity) > MAX_ACCURACY_M
        ? 'accuracy'
        : 'ok',
    };
  }

  /** Call when the player's hex changes; resets the dwell timer. */
  noteCellChange(cellKey, now = Date.now()) {
    if (cellKey !== this.lastCellKey) {
      this.lastCellKey = cellKey;
      this.cellEnteredAt = now;
    }
  }

  /** True iff player has been in the current cell long enough. */
  dwellSatisfied(now = Date.now()) {
    return this.cellEnteredAt != null && now - this.cellEnteredAt >= MIN_DWELL_MS;
  }

  status(now = Date.now()) {
    return {
      locked: now < this.lockedUntil,
      lockoutSecsLeft: Math.max(0, Math.ceil((this.lockedUntil - now) / 1000)),
      dwellMsLeft: this.cellEnteredAt
        ? Math.max(0, MIN_DWELL_MS - (now - this.cellEnteredAt))
        : MIN_DWELL_MS,
    };
  }
}
