# Territory Conquest — Starter

Location-based grab-the-block game built with **React Native (Expo) + react-native-maps + expo-location**. Walk into a grey cell on the map, tap **CAPTURE**, and it turns blue.

## Run it

```bash
cd territory-game
npm install
npx expo start
```

Open in the Expo Go app on your phone, or `i` / `a` to launch the iOS / Android simulator.

> On Android you'll get the OS map (Apple Maps on iOS) without a Google API key. Add a key under `expo.android.config.googleMaps.apiKey` in `app.json` to swap to Google Maps.

## What's included

- **Live map view** centred on the player with a custom orange marker.
- **Square grid overlay** — semi-transparent cells, configurable size (default 50 m).
- **Capture flow** — button enables only if you stand in an *unclaimed* cell.
- **Anti-cheat foundation** — capture is blocked while reported GPS accuracy is worse than 20 m.

## Customising the grid

`App.js` is intentionally one file. Tunables at the top:

```js
const CELL_SIZE_METERS = 50;
const RENDER_RADIUS_CELLS = 7;
const MAX_ALLOWED_ACCURACY_METERS = 20;
```

## Coordinate-to-Grid math (square)

Latitude is roughly linear (1° ≈ 111,320 m), so the *row* is just `floor(lat / latStep)`. Longitude shrinks with `cos(lat)` toward the poles, so the *column* step depends on the row's centre latitude:

```
latStep      = CELL / 111320
row          = floor(lat / latStep)
lonStep(row) = CELL / (111320 * cos(rowCenterLat))
col          = floor(lon / lonStep(row))
```

Each `(row, col)` is a stable integer key independent of where the player is now, so we can use it directly as the key in `capturedCells: { [row|col]: true }`.

## Switching to a hexagonal grid

Replace `latToRow` / `lonToCol` / `rowColToBounds` with an axial-coords hex scheme (e.g. flat-top, size = CELL_SIZE_METERS). Convert player lat/lng to local meters via an equirectangular projection around a reference origin, then use the standard `pixel_to_hex` formula. The rest of the app — state, capture rules, render loop — needs no changes.

## Next steps

- Persist `capturedCells` to `AsyncStorage` so progress survives a restart.
- Add a backend (Firebase / Supabase) to make captures global and enable rivals.
- Tighter anti-cheat: reject impossible speeds (>30 m/s ⇒ teleport), require a minimum dwell time per cell, sanity-check vs. the previous fix.
- Hex grid for prettier, equally-distant neighbours.
