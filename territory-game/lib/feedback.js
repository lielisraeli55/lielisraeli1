/**
 * Haptic / vibration feedback for player events.
 * Wraps expo-haptics and silently no-ops if the module isn't available.
 */

let Haptics = null;
try { Haptics = require('expo-haptics'); } catch {}

export async function captureSuccess() {
  if (!Haptics) return;
  try {
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
  } catch {}
}

export async function captureRejected() {
  if (!Haptics) return;
  try {
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
  } catch {}
}

export async function newCell() {
  if (!Haptics) return;
  try {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  } catch {}
}

export async function takeoverWarning() {
  if (!Haptics) return;
  try {
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
  } catch {}
}
