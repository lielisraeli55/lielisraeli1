# Territory Conquest

Location-based capture-the-block game. **React Native (Expo)** + **react-native-maps** + **expo-location**. Walk into a hex on the map, hold for a moment, tap **CAPTURE**, hex turns your colour. Step into someone else's hex and you can take it back — for double points.

## Run

```bash
cd territory-game
npm install
npx expo start
```

Open in **Expo Go** on a real phone (location can't be tested in a desktop simulator). On Android the OS map provider works without any API key — drop a Google key into `app.json` to switch to Google Maps.

## Features

| | |
| --- | --- |
| 🗺️ Live map | Centred on the player, custom marker in the player's chosen colour |
| ⬡ Hex grid | Flat-top, 28 m side, axial `(q, r)` coords stable globally |
| 🎯 Capture | Tap to claim the hex you're standing in |
| 🔁 Take-over | Step into someone else's hex and recapture it for **+25 pts** |
| 🛡️ Anti-cheat | Accuracy gate (≤20 m), dwell time (1.5 s), spoof-speed lockout (>30 m/s × 3) |
| 💾 AsyncStorage | Captures + profile persist across launches |
| 🏆 Scoreboard | Per-player cells / area / points, sorted by points |
| 🎨 Profiles | Pick a name + colour on first launch |
| 📳 Haptics | Buzz on capture / takeover / new-cell entry |
| ☁️ Cloud sync | Optional Firebase Realtime DB for multi-player (off by default) |

## Tunables

| File | Constant | Default | What it does |
| --- | --- | --- | --- |
| `lib/grid.js` | `HEX_SIZE_METERS` | 28 | Edge length of one hex |
| `lib/anticheat.js` | `MAX_ACCURACY_M` | 20 | Reject capture above this GPS error |
| `lib/anticheat.js` | `MAX_SPEED_MPS` | 30 | Speed above this counts as a spoof strike |
| `lib/anticheat.js` | `MIN_DWELL_MS` | 1500 | How long to stand inside a hex before claiming |
| `lib/score.js` | `POINTS_FRESH` | 10 | Points per first-time capture |
| `lib/score.js` | `POINTS_TAKEOVER` | 25 | Points when you steal another player's hex |

## How the hex math works

Each location is converted to local meters via an equirectangular projection that uses `cos(lat)` for the longitude axis, so cell sizes stay close to uniform on the ground:

```
xMeters = lon · 111 320 · cos(lat·π/180)
yMeters = lat · 111 320
```

Then the meters are projected onto **flat-top axial coords** with side `s = HEX_SIZE_METERS`:

```
qFloat =  (2/3) · x / s
rFloat = (-1/3) · x + (√3/3) · y / s
(q, r) = cubeRound(qFloat, rFloat)
```

`cubeRound` rounds to the lattice point with the smallest discrepancy along q/r/s where `s = -q - r`. Each `(q, r)` is the stable global key — independent of the player — used as the dictionary key for owners.

To render, the inverse converts a cell key to its 6 corner lat/lng pairs and feeds them straight into `<Polygon>`.

To swap to **square cells**, replace `latLonToHex` / `hexCorners` with row/col equivalents — nothing else changes.

## Anti-cheat

Three layers, each can be tuned independently:

1. **Accuracy gate** — reject capture while reported `accuracy > MAX_ACCURACY_M`.
2. **Dwell time** — entering a new hex resets a timer; capture allowed only after `MIN_DWELL_MS` continuous presence.
3. **Spoof speed** — every fix is compared against the previous one; if implied speed exceeds `MAX_SPEED_MPS` and the displacement is non-trivial, it accumulates "strikes". Hitting the strike threshold triggers a temporary lockout.

The lockout is short (15 s) so honest GPS jumps don't grief the player, but persistent fake-location apps that teleport will always hit it.

## Multi-player (optional)

`lib/cloud.js` is a thin wrapper around Firebase Realtime DB with `CLOUD_ENABLED = false` by default. To turn it on:

```bash
npm install firebase
```

Then in `lib/cloud.js`:

1. Paste your Firebase config (apiKey + databaseURL).
2. Flip `CLOUD_ENABLED = true`.

Schema is intentionally tiny — every cell key maps to `{ owner, color, capturedAt, score }`. The app subscribes to the whole map, merges with local cells (last-write-wins on `capturedAt`), and pushes every successful capture upstream. Test rules in Firebase are fine for development; tighten them with auth rules before release.

## File map

```
territory-game/
├── App.js                # screen + state machine
├── lib/
│   ├── grid.js           # hex math
│   ├── storage.js        # AsyncStorage
│   ├── anticheat.js      # accuracy / dwell / speed
│   ├── score.js          # leaderboard derivation
│   ├── feedback.js       # haptics
│   └── cloud.js          # Firebase sync (optional)
├── package.json
├── app.json
└── README.md
```
