import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
  Modal,
  ScrollView,
  Alert,
} from 'react-native';
import MapView, { Marker, Polygon } from 'react-native-maps';
import * as Location from 'expo-location';

import {
  HEX_SIZE_METERS,
  HEX_AREA_M2,
  latLonToHex,
  hexCorners,
  hexKey,
  neighbourhood,
} from './lib/grid';
import { loadCells, saveCells, loadProfile, saveProfile } from './lib/storage';
import { AntiCheat, MAX_ACCURACY_M, MIN_DWELL_MS } from './lib/anticheat';
import { scoreboard, POINTS_FRESH, POINTS_TAKEOVER, formatArea } from './lib/score';
import { captureSuccess, captureRejected, newCell, takeoverWarning } from './lib/feedback';
import { CLOUD_ENABLED, subscribeCells, pushCapture } from './lib/cloud';

const PLAYER_COLORS = [
  { name: 'Cobalt',  hex: '#1976D2' },
  { name: 'Crimson', hex: '#E53935' },
  { name: 'Forest',  hex: '#2E7D32' },
  { name: 'Amber',   hex: '#FB8C00' },
  { name: 'Violet',  hex: '#8E24AA' },
  { name: 'Cyan',    hex: '#00ACC1' },
];

const RENDER_RADIUS_HEX = 6;

export default function App() {
  /* ---- Location ---- */
  const [location, setLocation] = useState(null);
  const [accuracy, setAccuracy] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);

  /* ---- Profile ---- */
  const [profile, setProfile] = useState(null);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [draftName, setDraftName] = useState('');
  const [draftColor, setDraftColor] = useState(PLAYER_COLORS[0]);

  /* ---- Cells ---- */
  const [cells, setCells] = useState({}); // { "q|r": { owner, color, capturedAt, score } }
  const [showScoreboard, setShowScoreboard] = useState(false);

  /* ---- Refs ---- */
  const antiCheatRef = useRef(new AntiCheat());
  const lastCellKeyRef = useRef(null);
  const dwellTickRef = useRef(0);
  const [, forceTick] = useState(0); // used to redraw the dwell countdown each second

  /* ---- Boot: load profile + cells, request location ---- */
  useEffect(() => {
    (async () => {
      const [p, c] = await Promise.all([loadProfile(), loadCells()]);
      if (p?.name) setProfile(p);
      else {
        setShowProfileModal(true);
      }
      setCells(c);

      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        setErrorMsg('Location permission denied. The game needs your GPS.');
        return;
      }
    })();
  }, []);

  /* ---- Location subscription (only after profile present) ---- */
  useEffect(() => {
    if (!profile) return;
    let sub;
    (async () => {
      sub = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.BestForNavigation,
          timeInterval: 1000,
          distanceInterval: 1,
        },
        (loc) => {
          const coords = { latitude: loc.coords.latitude, longitude: loc.coords.longitude };
          setLocation(coords);
          setAccuracy(loc.coords.accuracy);
          antiCheatRef.current.acceptFix(coords, loc.coords.accuracy, loc.timestamp);
        }
      );
    })();
    return () => sub?.remove();
  }, [profile]);

  /* ---- Cloud sync (optional) ---- */
  useEffect(() => {
    if (!CLOUD_ENABLED) return;
    const unsub = subscribeCells((remote) => {
      // Last-write-wins on capturedAt
      setCells((local) => {
        const merged = { ...local };
        for (const k of Object.keys(remote)) {
          const r = remote[k];
          const m = merged[k];
          if (!m || (r.capturedAt ?? 0) > (m.capturedAt ?? 0)) merged[k] = r;
        }
        saveCells(merged);
        return merged;
      });
    });
    return unsub;
  }, []);

  /* ---- Periodic tick for dwell countdown ---- */
  useEffect(() => {
    const id = setInterval(() => {
      dwellTickRef.current++;
      forceTick((n) => n + 1);
    }, 250);
    return () => clearInterval(id);
  }, []);

  /* ---- Current hex ---- */
  const currentHex = useMemo(() => {
    if (!location) return null;
    return latLonToHex(location.latitude, location.longitude);
  }, [location]);

  const currentKey = currentHex ? hexKey(currentHex.q, currentHex.r) : null;

  /* ---- Notify anti-cheat of cell change + haptic on entry ---- */
  useEffect(() => {
    if (!currentKey) return;
    if (lastCellKeyRef.current !== currentKey) {
      antiCheatRef.current.noteCellChange(currentKey);
      newCell();
      const owner = cells[currentKey]?.owner;
      if (owner && owner !== profile?.name) takeoverWarning();
      lastCellKeyRef.current = currentKey;
    }
  }, [currentKey, cells, profile]);

  /* ---- Visible hexes ---- */
  const visibleHexes = useMemo(() => {
    if (!currentHex) return [];
    return neighbourhood(currentHex.q, currentHex.r, RENDER_RADIUS_HEX);
  }, [currentHex]);

  /* ---- Capture eligibility ---- */
  const acStatus = antiCheatRef.current.status();
  const accuracyOk = accuracy != null && accuracy <= MAX_ACCURACY_M;
  const dwellOk = antiCheatRef.current.dwellSatisfied();
  const owned = currentKey ? cells[currentKey] : null;
  const ownedByMe = owned?.owner === profile?.name;
  const canCapture = !!currentHex && !!profile && accuracyOk && dwellOk && !acStatus.locked && !ownedByMe;

  const captureLabel = (() => {
    if (!profile) return 'מגדיר פרופיל...';
    if (!accuracyOk) return `GPS לא מספיק מדויק (${accuracy?.toFixed(0) ?? '?'} m)`;
    if (acStatus.locked) return `נחסם זמנית (${acStatus.lockoutSecsLeft}s) — חשד לזיוף מיקום`;
    if (!dwellOk) return `החזק במקום (${(acStatus.dwellMsLeft / 1000).toFixed(1)}s)`;
    if (ownedByMe) return 'התא כבר שלך';
    if (owned) return `כבוש ע"י ${owned.owner} — כבוש מחדש (+${POINTS_TAKEOVER})`;
    return `כבוש את התא (+${POINTS_FRESH})`;
  })();

  /* ---- Capture handler ---- */
  const onCapture = useCallback(async () => {
    if (!canCapture || !currentHex || !profile) {
      captureRejected();
      return;
    }
    const k = hexKey(currentHex.q, currentHex.r);
    const isTakeover = !!cells[k]?.owner && cells[k]?.owner !== profile.name;
    const payload = {
      owner: profile.name,
      color: profile.color.hex,
      capturedAt: Date.now(),
      score: isTakeover ? POINTS_TAKEOVER : POINTS_FRESH,
    };
    const next = { ...cells, [k]: payload };
    setCells(next);
    await saveCells(next);
    if (CLOUD_ENABLED) pushCapture(k, payload).catch(() => {});
    captureSuccess();
    // Reset dwell so the player has to leave + come back to recapture
    antiCheatRef.current.cellEnteredAt = Date.now();
  }, [canCapture, currentHex, profile, cells]);

  /* ---- Profile setup ---- */
  const onSaveProfile = useCallback(async () => {
    const name = draftName.trim().slice(0, 20) || 'Player';
    const p = { name, color: draftColor };
    setProfile(p);
    await saveProfile(p);
    setShowProfileModal(false);
  }, [draftName, draftColor]);

  /* ---- Render ---- */
  if (errorMsg) {
    return (
      <View style={styles.center}><Text style={styles.error}>{errorMsg}</Text></View>
    );
  }

  if (!profile && !showProfileModal) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {!location ? (
        <View style={styles.center}>
          <ActivityIndicator size="large" color="#1976D2" />
          <Text style={styles.loadingText}>מאתר GPS...</Text>
        </View>
      ) : (
        <MapView
          style={styles.map}
          initialRegion={{
            latitude: location.latitude,
            longitude: location.longitude,
            latitudeDelta: 0.003,
            longitudeDelta: 0.003,
          }}
          showsUserLocation={false}
          showsCompass
        >
          {visibleHexes.map(({ q, r }) => {
            const k = hexKey(q, r);
            const c = cells[k];
            const corners = hexCorners(q, r);
            const isCurrent = currentKey === k;
            const fill = c
              ? hexAlpha(c.color, isCurrent ? 0.65 : 0.45)
              : isCurrent
              ? 'rgba(255, 235, 59, 0.30)'
              : 'rgba(120, 120, 120, 0.16)';
            const stroke = c
              ? c.color
              : isCurrent
              ? 'rgba(255, 193, 7, 0.95)'
              : 'rgba(80, 80, 80, 0.5)';
            return (
              <Polygon
                key={k}
                coordinates={corners}
                fillColor={fill}
                strokeColor={stroke}
                strokeWidth={isCurrent ? 2 : 1}
              />
            );
          })}

          <Marker coordinate={location} anchor={{ x: 0.5, y: 0.5 }}>
            <View style={[styles.playerOuter, { borderColor: profile?.color?.hex ?? '#FF5722' }]}>
              <View style={[styles.playerInner, { backgroundColor: profile?.color?.hex ?? '#FF5722' }]} />
            </View>
          </Marker>
        </MapView>
      )}

      {/* HUD */}
      <View style={styles.hud}>
        <Text style={styles.hudLine}>
          GPS: {accuracy != null ? `${accuracy.toFixed(0)} m` : '—'} {accuracyOk ? '✓' : '⚠︎'}{'   '}
          Hex: {currentHex ? `${currentHex.q},${currentHex.r}` : '—'}
        </Text>
        <Text style={styles.hudLine}>
          Player: <Text style={{ color: profile?.color?.hex ?? '#fff' }}>{profile?.name ?? '...'}</Text>
        </Text>
      </View>

      {/* Score pill (tap → scoreboard) */}
      <TouchableOpacity style={styles.scorePill} onPress={() => setShowScoreboard(true)}>
        <Text style={styles.scorePillText}>
          {scoreboardSummary(cells, profile?.name)}
        </Text>
      </TouchableOpacity>

      {/* Capture button */}
      <TouchableOpacity
        style={[
          styles.captureBtn,
          !canCapture && styles.captureBtnDisabled,
          owned && !ownedByMe && canCapture && { backgroundColor: '#E53935' },
        ]}
        onPress={onCapture}
        disabled={!canCapture}
        activeOpacity={0.85}
      >
        <Text style={styles.captureBtnText}>{captureLabel}</Text>
      </TouchableOpacity>

      {/* Profile modal */}
      <Modal visible={showProfileModal} transparent animationType="slide" onRequestClose={() => {}}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>הגדרת שחקן</Text>
            <TextInput
              value={draftName}
              onChangeText={setDraftName}
              placeholder="שם שחקן"
              placeholderTextColor="#888"
              style={styles.input}
              maxLength={20}
            />
            <Text style={styles.modalSub}>בחר צבע:</Text>
            <View style={styles.colorRow}>
              {PLAYER_COLORS.map((c) => (
                <TouchableOpacity
                  key={c.hex}
                  onPress={() => setDraftColor(c)}
                  style={[
                    styles.colorSwatch,
                    { backgroundColor: c.hex },
                    draftColor.hex === c.hex && styles.colorSwatchActive,
                  ]}
                />
              ))}
            </View>
            <TouchableOpacity style={styles.modalBtn} onPress={onSaveProfile}>
              <Text style={styles.modalBtnText}>שמור והתחל</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>

      {/* Scoreboard modal */}
      <Modal visible={showScoreboard} transparent animationType="fade" onRequestClose={() => setShowScoreboard(false)}>
        <TouchableOpacity style={styles.modalBackdrop} activeOpacity={1} onPress={() => setShowScoreboard(false)}>
          <View style={styles.modalCard} pointerEvents="box-none">
            <Text style={styles.modalTitle}>טבלת ניקוד</Text>
            <ScrollView style={{ maxHeight: 320 }}>
              {scoreboard(cells).length === 0 ? (
                <Text style={styles.modalSub}>אין עדיין כיבושים. צא החוצה ותתחיל!</Text>
              ) : (
                scoreboard(cells).map((row) => (
                  <View key={row.owner} style={styles.scoreRow}>
                    <View style={[styles.colorDot, { backgroundColor: row.color }]} />
                    <Text style={[styles.scoreName, row.owner === profile?.name && { fontWeight: 'bold' }]}>
                      {row.owner}
                    </Text>
                    <Text style={styles.scoreCells}>{row.cells} cells</Text>
                    <Text style={styles.scoreArea}>{formatArea(row.areaKm2)}</Text>
                    <Text style={styles.scorePoints}>{row.points} pts</Text>
                  </View>
                ))
              )}
            </ScrollView>
            <TouchableOpacity style={styles.modalBtn} onPress={() => setShowScoreboard(false)}>
              <Text style={styles.modalBtnText}>סגור</Text>
            </TouchableOpacity>
          </View>
        </TouchableOpacity>
      </Modal>
    </View>
  );
}

function scoreboardSummary(cells, me) {
  const board = scoreboard(cells);
  if (board.length === 0) return 'אין כיבושים — לחץ לטבלה';
  const mine = board.find((s) => s.owner === me);
  const top = board[0];
  if (!mine) return `מוביל: ${top.owner} · ${top.points} pts`;
  return `${mine.points} pts · ${mine.cells} cells · ${formatArea(mine.areaKm2)}`;
}

function hexAlpha(hex, alpha) {
  const h = hex.replace('#', '');
  const r = parseInt(h.substring(0, 2), 16);
  const g = parseInt(h.substring(2, 4), 16);
  const b = parseInt(h.substring(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#000' },
  map: { ...StyleSheet.absoluteFillObject },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#fff' },
  error: { color: '#d32f2f', padding: 20, textAlign: 'center', fontSize: 16 },
  loadingText: { marginTop: 16, fontSize: 16, color: '#555' },

  hud: {
    position: 'absolute',
    top: 50,
    left: 16,
    right: 16,
    backgroundColor: 'rgba(0,0,0,0.65)',
    borderRadius: 10,
    padding: 12,
  },
  hudLine: { color: '#fff', fontSize: 14, fontFamily: 'Courier', marginVertical: 1 },

  scorePill: {
    position: 'absolute',
    top: 130,
    alignSelf: 'center',
    backgroundColor: 'rgba(0,0,0,0.65)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
  },
  scorePillText: { color: '#fff', fontSize: 13, letterSpacing: 0.5 },

  captureBtn: {
    position: 'absolute',
    bottom: 40,
    left: 24,
    right: 24,
    backgroundColor: '#1976D2',
    borderRadius: 14,
    paddingVertical: 18,
    alignItems: 'center',
    elevation: 6,
  },
  captureBtnDisabled: { backgroundColor: '#666' },
  captureBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 15, letterSpacing: 0.5, textAlign: 'center' },

  playerOuter: {
    width: 28, height: 28, borderRadius: 14,
    borderWidth: 2,
    backgroundColor: 'rgba(255, 255, 255, 0.4)',
    alignItems: 'center', justifyContent: 'center',
  },
  playerInner: { width: 14, height: 14, borderRadius: 7, borderColor: '#fff', borderWidth: 2 },

  modalBackdrop: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', alignItems: 'center', justifyContent: 'center',
  },
  modalCard: {
    width: '85%', backgroundColor: '#1c1c22', borderRadius: 16, padding: 22,
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.12)',
  },
  modalTitle: { color: '#fff', fontSize: 20, fontWeight: 'bold', textAlign: 'center', marginBottom: 14 },
  modalSub: { color: '#bbb', fontSize: 14, textAlign: 'center', marginVertical: 8 },
  input: {
    backgroundColor: '#0d0d12', color: '#fff',
    borderRadius: 8, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16,
    borderWidth: 1, borderColor: 'rgba(255,255,255,0.18)',
  },
  colorRow: { flexDirection: 'row', justifyContent: 'space-around', marginVertical: 14 },
  colorSwatch: {
    width: 36, height: 36, borderRadius: 18, borderWidth: 2, borderColor: 'transparent',
  },
  colorSwatchActive: { borderColor: '#fff', transform: [{ scale: 1.18 }] },
  modalBtn: {
    backgroundColor: '#1976D2', borderRadius: 10, paddingVertical: 14,
    alignItems: 'center', marginTop: 8,
  },
  modalBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 16, letterSpacing: 1 },

  scoreRow: {
    flexDirection: 'row', alignItems: 'center', paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: '#333',
  },
  colorDot: { width: 14, height: 14, borderRadius: 7, marginRight: 10 },
  scoreName: { color: '#fff', fontSize: 14, flex: 1 },
  scoreCells: { color: '#aaa', fontSize: 12, width: 65, textAlign: 'right' },
  scoreArea: { color: '#aaa', fontSize: 12, width: 90, textAlign: 'right' },
  scorePoints: { color: '#1976D2', fontSize: 14, fontWeight: 'bold', width: 60, textAlign: 'right' },
});
