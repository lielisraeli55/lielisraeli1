import AsyncStorage from '@react-native-async-storage/async-storage';

const K_CELLS = 'tc.cells.v2';
const K_PROFILE = 'tc.profile.v1';

/** Cells shape: { "q|r": { owner, color, capturedAt } } */

export async function loadCells() {
  try {
    const json = await AsyncStorage.getItem(K_CELLS);
    return json ? JSON.parse(json) : {};
  } catch {
    return {};
  }
}

export async function saveCells(cells) {
  try {
    await AsyncStorage.setItem(K_CELLS, JSON.stringify(cells));
  } catch {}
}

export async function loadProfile() {
  try {
    const json = await AsyncStorage.getItem(K_PROFILE);
    return json ? JSON.parse(json) : null;
  } catch {
    return null;
  }
}

export async function saveProfile(profile) {
  try {
    await AsyncStorage.setItem(K_PROFILE, JSON.stringify(profile));
  } catch {}
}

export async function clearAll() {
  try {
    await AsyncStorage.multiRemove([K_CELLS, K_PROFILE]);
  } catch {}
}
