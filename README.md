# lielisraeli1 — אוסף אתרים ואפליקציות

הריפו מארח כמה פרויקטים נפרדים שמפורסמים דרך GitHub Pages.

## 🟡 Sagi Israeli — פרופיל שחקן

האתר הראשי. פרופיל מעמיק על השחקן שגיא ישראלי (קשר התקפי, בית״ר ירושלים, מס׳ 28, בהשאלה לעירוני מודיעין). ביוגרפיה, סטטיסטיקה, ציר זמן קריירה, משחקים בולטים והעברות.

- כתובת: `https://lielisraeli55.github.io/lielisraeli1/`
- קבצים: `index.html`, `styles.css`, `script.js`

## ✋ Air Touch — שליטה במסך הטלפון בתנועות יד

PWA שמאפשרת לשלוט במסך הטלפון עם המצלמה — הצבעה באצבע מזיזה סמן, צביטה (אגודל + אצבע) פותחת אפליקציה. מבוסס MediaPipe Hands.

- כתובת: `https://lielisraeli55.github.io/lielisraeli1/air-touch/`
- מותקנת כ-PWA על מסך הבית, עובדת offline.

### התקנה כאפליקציה
1. פתח בכרום בטלפון: `https://lielisraeli55.github.io/lielisraeli1/air-touch/`
2. ⋮ → **"Add to Home screen"** / **"Install app"** (או באייפון: שתף → "הוסף למסך הבית")

### בניית APK דרך PWA Builder
1. פתח `https://www.pwabuilder.com/`
2. הדבק: `https://lielisraeli55.github.io/lielisraeli1/air-touch/`
3. **Package For Stores** → **Android** → **Download**
4. תקבל `app-release-signed.apk` להתקנה ישירה.

### איך זה עובד טכנית
- **MediaPipe Hands** — 21 נקודות ציון ליד בזמן אמת
- **Index fingertip → cursor** עם השתקפות אופקית (מצלמה קדמית)
- **EMA smoothing** alpha=0.3 על תנועת הסמן
- **Pinch detection** — יחס מרחק אגודל-אצבע לגודל היד, hysteresis 0.40/0.55
- **Service worker** — cache-first ל-MediaPipe, network-first עם fallback לשאר
- כל הנתונים נשארים מקומית במכשיר

## מבנה הריפו

```
.
├── index.html              ← פרופיל שגיא ישראלי
├── styles.css
├── script.js
├── air-touch/              ← Air Touch PWA
│   ├── index.html
│   ├── style.css
│   ├── script.js
│   ├── manifest.webmanifest
│   ├── service-worker.js
│   └── icons/
├── territory-game/         ← פרויקט נפרד
├── native/                 ← פרויקט Android נפרד
├── voice/                  ← פרויקט Android נפרד
├── .nojekyll
└── README.md
```
