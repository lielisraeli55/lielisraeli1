import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  StyleSheet,
  View,
  Text,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import MapView, { Marker, Polygon } from 'react-native-maps';
import * as Location from 'expo-location';

/* ====================================================================
 * Territory Conquest — Starter
 *
 * Live map + square grid overlay. Walk into an Unclaimed (grey) cell,
 * tap "CAPTURE", and it turns Captured (blue). The button is disabled
 * unless GPS accuracy is below the anti-cheat threshold.
 *
 * --- Coordinate-to-Grid math ---
 * The grid is anchored at lat = 0, lon = 0. Each cell is
 * CELL_SIZE_METERS on a side. Latitude has a (near-)constant
 * meters-per-degree (~111,320 m), so a row index is just
 *     row = floor(lat / latStep), latStep = CELL / 111320
 *
 * Longitude is trickier: 1 degree of longitude shrinks toward the
 * poles by cos(lat). So for a cell to be ~CELL meters wide on the
 * ground, the column step depends on the row:
 *     lonStep(row) = CELL / (111320 * cos(rowCenterLat))
 * and  col = floor(lon / lonStep(row)).
 *
 * This gives us a clean integer (row, col) key for every point on
 * the planet that's stable as the player moves around. To swap to a
 * hexagonal grid later, replace `latToRow`/`lonToCol`/`rowColToBounds`
 * with a flat-top or pointy-top axial-coords scheme — the rest of the
 * file (state, capture logic, rendering) does not have to change.
 * ====================================================================*/

const CELL_SIZE_METERS = 50;
const RENDER_RADIUS_CELLS = 7;          // 15×15 = 225 cells around the player
const MAX_ALLOWED_ACCURACY_METERS = 20; // anti-cheat: must be <= this to capture

const METERS_PER_DEG_LAT = 111320;
const LAT_STEP_DEG = CELL_SIZE_METERS / METERS_PER_DEG_LAT;

function latToRow(lat) {
  return Math.floor(lat / LAT_STEP_DEG);
}

function rowToCenterLat(row) {
  return (row + 0.5) * LAT_STEP_DEG;
}

function lonStepForRow(row) {
  const centerLat = rowToCenterLat(row);
  return CELL_SIZE_METERS / (METERS_PER_DEG_LAT * Math.cos((centerLat * Math.PI) / 180));
}

function lonToCol(lon, row) {
  return Math.floor(lon / lonStepForRow(row));
}

function rowColToBounds(row, col) {
  const minLat = row * LAT_STEP_DEG;
  const maxLat = (row + 1) * LAT_STEP_DEG;
  const lonStep = lonStepForRow(row);
  const minLon = col * lonStep;
  const maxLon = (col + 1) * lonStep;
  return [
    { latitude: minLat, longitude: minLon },
    { latitude: minLat, longitude: maxLon },
    { latitude: maxLat, longitude: maxLon },
    { latitude: maxLat, longitude: minLon },
  ];
}

const cellKey = (row, col) => `${row}|${col}`;

export default function App() {
  const [location, setLocation] = useState(null);
  const [accuracy, setAccuracy] = useState(null);
  const [errorMsg, setErrorMsg] = useState(null);
  const [capturedCells, setCapturedCells] = useState({}); // { "row|col": true }
  const mapRef = useRef(null);

  // --- 1. Location subscription ---------------------------------------
  useEffect(() => {
    let subscription;

    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== 'granted') {
        setErrorMsg('הרשאת מיקום נדחתה / Location permission denied.');
        return;
      }

      subscription = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.BestForNavigation,
          timeInterval: 1000,
          distanceInterval: 1,
        },
        (loc) => {
          setLocation({
            latitude: loc.coords.latitude,
            longitude: loc.coords.longitude,
          });
          setAccuracy(loc.coords.accuracy);
        }
      );
    })();

    return () => {
      subscription?.remove();
    };
  }, []);

  // --- 2. Which cell is the player in right now? ----------------------
  const currentCell = useMemo(() => {
    if (!location) return null;
    const row = latToRow(location.latitude);
    const col = lonToCol(location.longitude, row);
    return { row, col };
  }, [location]);

  // --- 3. Visible cells around the player ------------------------------
  const visibleCells = useMemo(() => {
    if (!currentCell) return [];
    const cells = [];
    for (let dr = -RENDER_RADIUS_CELLS; dr <= RENDER_RADIUS_CELLS; dr++) {
      for (let dc = -RENDER_RADIUS_CELLS; dc <= RENDER_RADIUS_CELLS; dc++) {
        const row = currentCell.row + dr;
        const col = currentCell.col + dc;
        const key = cellKey(row, col);
        cells.push({ row, col, key, captured: !!capturedCells[key] });
      }
    }
    return cells;
  }, [currentCell, capturedCells]);

  // --- 4. Capture eligibility -----------------------------------------
  const currentCellKey = currentCell ? cellKey(currentCell.row, currentCell.col) : null;
  const accuracyOk = accuracy != null && accuracy <= MAX_ALLOWED_ACCURACY_METERS;
  const alreadyOwned = currentCellKey != null && !!capturedCells[currentCellKey];
  const canCapture = !!currentCell && accuracyOk && !alreadyOwned;

  const onCapture = () => {
    if (!canCapture || !currentCell) return;
    const key = cellKey(currentCell.row, currentCell.col);
    setCapturedCells((prev) => ({ ...prev, [key]: true }));
  };

  // --- 5. Render -------------------------------------------------------
  if (errorMsg) {
    return (
      <View style={styles.center}>
        <Text style={styles.error}>{errorMsg}</Text>
      </View>
    );
  }
  if (!location) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#1976D2" />
        <Text style={styles.loadingText}>מאתר GPS...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <MapView
        ref={mapRef}
        style={styles.map}
        initialRegion={{
          latitude: location.latitude,
          longitude: location.longitude,
          latitudeDelta: 0.004,
          longitudeDelta: 0.004,
        }}
        showsUserLocation={false}
        showsCompass
      >
        {/* Grid */}
        {visibleCells.map((cell) => (
          <Polygon
            key={cell.key}
            coordinates={rowColToBounds(cell.row, cell.col)}
            fillColor={
              cell.captured
                ? 'rgba(33, 150, 243, 0.45)'
                : 'rgba(120, 120, 120, 0.18)'
            }
            strokeColor={
              cell.captured
                ? 'rgba(13, 71, 161, 0.9)'
                : 'rgba(80, 80, 80, 0.5)'
            }
            strokeWidth={1}
          />
        ))}

        {/* Player marker */}
        <Marker coordinate={location} anchor={{ x: 0.5, y: 0.5 }}>
          <View style={styles.playerOuter}>
            <View style={styles.playerInner} />
          </View>
        </Marker>
      </MapView>

      {/* HUD */}
      <View style={styles.hud}>
        <Text style={styles.hudLine}>
          GPS Accuracy: {accuracy != null ? `${accuracy.toFixed(0)} m` : '—'} {accuracyOk ? '✓' : '⚠︎'}
        </Text>
        <Text style={styles.hudLine}>
          Captured cells: {Object.keys(capturedCells).length}
        </Text>
        {currentCell && (
          <Text style={styles.hudLineMuted}>
            cell ({currentCell.row}, {currentCell.col})
          </Text>
        )}
      </View>

      {/* Capture button */}
      <TouchableOpacity
        style={[styles.captureBtn, !canCapture && styles.captureBtnDisabled]}
        onPress={onCapture}
        disabled={!canCapture}
        activeOpacity={0.85}
      >
        <Text style={styles.captureBtnText}>
          {canCapture
            ? 'CAPTURE THIS CELL'
            : !accuracyOk
            ? `GPS too imprecise (${accuracy ? accuracy.toFixed(0) : '?'} m)`
            : alreadyOwned
            ? 'Already yours'
            : 'Move to an unclaimed cell'}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
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
  hudLineMuted: { color: '#9aa0a6', fontSize: 12, fontFamily: 'Courier', marginTop: 4 },

  captureBtn: {
    position: 'absolute',
    bottom: 40,
    left: 24,
    right: 24,
    backgroundColor: '#1976D2',
    borderRadius: 14,
    paddingVertical: 18,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 3 },
    elevation: 6,
  },
  captureBtnDisabled: { backgroundColor: '#9e9e9e', shadowOpacity: 0 },
  captureBtnText: { color: '#fff', fontWeight: 'bold', fontSize: 16, letterSpacing: 1 },

  playerOuter: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: 'rgba(255, 87, 34, 0.35)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  playerInner: {
    width: 14,
    height: 14,
    borderRadius: 7,
    backgroundColor: '#FF5722',
    borderColor: '#fff',
    borderWidth: 2,
  },
});
