# lielisraeli1

ריפו עם שני פרויקטים:

1. **Air Touch** — אפליקציית טלפון שנשלטת ביד דרך מצלמה (PWA)
2. **AI Image Generator** — מחולל תמונות AI מאפס (DCGAN ב-Colab)

---

# 1. AI Image Generator — מחולל תמונות AI מאפס

פרויקט שבונה AI ליצירת תמונות **מאפס** - בלי להשתמש במודלים מוכנים כמו Stable Diffusion או DALL-E.

## איך להריץ?

### דרך הטלפון (הכי פשוט):

1. היכנס ל: https://colab.research.google.com
2. לחץ על **File → Upload notebook**
3. העלה את הקובץ `image_generator.ipynb` מהריפו הזה
4. לחץ על **Runtime → Change runtime type → GPU** (T4 חינמי)
5. לחץ על **Runtime → Run all**
6. חכה 10-15 דקות וצפה בקסם

### דרך GitHub ישירות:

לחץ על הקובץ `image_generator.ipynb` בריפו → לחץ על **"Open in Colab"**

## מה הפרויקט עושה?

בונה רשת **DCGAN** (Deep Convolutional Generative Adversarial Network):

- **Generator** - יוצר תמונות מרעש אקראי
- **Discriminator** - שופט אם תמונה אמיתית או מזויפת
- שתיהן לומדות יחד עד שה-Generator יוצר תמונות שנראות אמיתיות

## דרישות

- חשבון Google (לקולאב)
- אפס התקנות במחשב/טלפון
- אפס ידע מוקדם בתכנות

## מה תקבל בסוף?

- מודל מאומן שיודע ליצור ספרות בכתב יד
- אפשרות לייצר אינסוף תמונות חדשות
- קוד שאתה יכול להרחיב לסוגי תמונות אחרים (פרצופים, בגדים, וכו')

## הרחבות אפשריות

בתוך ה-Notebook יש הוראות איך להחליף את סוג התמונות (בגדים, צבע, גודל גדול יותר).

---

# 2. Air Touch — אפליקציית טלפון שנשלטת ביד

מסך-בית וירטואלי שנשלט בתנועות יד דרך מצלמת הטלפון. מצביעים באצבע — סמן זוהר עוקב. צביטה (אגודל + אצבע) — האפליקציה מתחת לסמן נפתחת.

האתר בנוי כ-**PWA** (Progressive Web App) מלא: manifest, service worker, אייקונים, תמיכה ב-offline. אפשר להתקין כאפליקציה אמיתית על מסך הבית של הטלפון, ואפשר לארוז כ-APK חתום באמצעות PWA Builder.

---

## אפשרות 1 — התקנה ישירה מהדפדפן (הכי מהיר, ~10 שניות)

זו אפליקציה אמיתית: אייקון על מסך הבית, fullscreen, גישה למצלמה, עובדת גם offline.

1. פתח בכרום בטלפון:
   ```
   https://lielisraeli55.github.io/lielisraeli1/
   ```
2. בכרום אנדרואיד תופיע הצעה אוטומטית "Add Air Touch to home screen", או:
   - לחץ על שלוש הנקודות ⋮ למעלה
   - בחר **"Add to Home screen"** / **"Install app"**
3. אישור → אייקון "Air Touch" יופיע במגירת האפליקציות. הקלקה עליו פותחת ב-fullscreen בלי שורת כתובת.

באייפון (Safari): שתף → "הוסף למסך הבית".

---

## אפשרות 2 — APK אמיתי דרך PWA Builder (חינם, ~5 דקות)

PWA Builder הוא כלי רשמי של Microsoft שלוקח כתובת PWA ומחזיר חבילת APK חתומה לאנדרואיד. זה לא wrapping זול — זה **TWA (Trusted Web Activity)**, הסטנדרט המודרני להפצת PWA כאפליקציה ב-Google Play.

1. במחשב או בטלפון פתח: `https://www.pwabuilder.com/`
2. הדבק את הכתובת:
   ```
   https://lielisraeli55.github.io/lielisraeli1/
   ```
3. לחץ **Start** → PWA Builder יסרוק את ה-manifest והשירות-עובד ויראה דירוג. כל הסעיפים אמורים לקבל ✓.
4. לחץ **Package For Stores** → בחר **Android** → **Download**.
5. תקבל קובץ ZIP. בתוכו:
   - `app-release-signed.apk` ← הקובץ להתקנה
   - `signing.keystore` ← שמור! (לעדכוני גרסה עתידיים)
   - הוראות העלאה ל-Google Play
6. העבר את ה-APK לטלפון (USB / Drive / Telegram), לחץ עליו → אנדרואיד יבקש אישור להתקנה ממקור לא ידוע (Settings → Install unknown apps → הפעל לדפדפן/מנהל הקבצים שדרכו פתחת).
7. ההתקנה תסתיים, האפליקציה תופיע במגירה כאפליקציה רגילה לחלוטין.

---

## למה לא APK שאני בונה במקומך?

יצירת APK דורשת Android SDK + JDK + Gradle ופעולת build של דקות, וקובץ הפלט לא מתאים לאחסון ב-Git. PWA Builder עושה את זה בענן בחינם, מחזיר APK חתום מקצועי, ולא דורש להתקין כלום מקומית.

אם בכל זאת תרצה build מקומי בעתיד, השתמש ב-[Bubblewrap CLI](https://github.com/GoogleChromeLabs/bubblewrap):
```bash
npx @bubblewrap/cli init --manifest=https://lielisraeli55.github.io/lielisraeli1/manifest.webmanifest
npx @bubblewrap/cli build
```

---

## איך זה עובד

- **MediaPipe Hands** — 21 נקודות ציון ליד בזמן אמת
- **Index fingertip → cursor**: מיפוי לקואורדינטות מסך עם השתקפות אופקית (front camera mirror)
- **EMA smoothing**: alpha=0.3 על תנועת הסמן להפחתת רעידות
- **Pinch detection**: יחס מרחק אגודל-אצבע לגודל היד, hysteresis 0.40/0.55
- **Service worker**: cache-first ל-MediaPipe, network-first עם fallback ל-cache לשאר
- **fullscreen + portrait** ב-manifest, theme-color ירוק
- כל הנתונים מקומיים — שום דבר לא יוצא מהמכשיר

## מבנה הקבצים

```
.
├── index.html
├── style.css
├── script.js
├── manifest.webmanifest    # PWA manifest
├── service-worker.js       # offline cache
├── icons/
│   ├── icon-192.png
│   ├── icon-512.png
│   ├── icon-maskable-192.png
│   ├── icon-maskable-512.png
│   ├── apple-touch-icon.png
│   └── favicon-32.png
├── .nojekyll
├── image_generator.ipynb   # Colab notebook (DCGAN)
└── README.md
```
