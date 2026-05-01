# Proximity Sound — אפליקציית אנדרואיד

אפליקציית אנדרואיד פשוטה (Kotlin / Android Studio) שמשתמשת ב**חיישן הקרבה (Proximity Sensor)** של הטלפון — זה החיישן הקטן בחזית הטלפון ליד הרמקול העליון, אותו חיישן שמכבה את המסך כשמצמידים את הטלפון לאוזן בשיחה.

## מה האפליקציה עושה

* מקשיבה לחיישן הקרבה של המכשיר.
* כשמשהו מתקרב לחיישן (פחות מ־1.5 ס״מ בערך) — היא **משמיעה את הסאונד שב-`res/raw/sound.*`**.
* כשמתרחקים — הסאונד מפסיק.
* על המסך מוצג מרחק רגעי מהחיישן + סטטוס "קרוב/רחוק".

> ⚠️ חיישן הקרבה רוב הזמן הוא **דיגיטלי** (binary): הוא מחזיר רק "קרוב" או "רחוק" עם ערך קבוע (בד״כ 0 cm כשקרוב ו-`maximumRange` כשרחוק). חלק מהמכשירים כן נותנים ערכים אנלוגיים — באלה תראה גם ערכי ביניים.

## איך מחליפים את הסאונד

הסאונד שמופעל הוא הקובץ:

```
proximity-sound/app/src/main/res/raw/sound.wav
```

כברירת מחדל מצורף **טון בדיקה קצר (beep 880Hz)** רק כדי שה-APK יבנה ויפעל מיד מהקופסה.

כדי לשים את הסאונד שלך מהיוטיוב:

1. הורד את האודיו של הסרטון בעצמך (אני לא יכול להוריד מיוטיוב — זה מנוגד לתנאי השימוש שלהם, אבל אתה יכול להשתמש בכלי הקלטה / ממיר חוקי על תוכן שיש לך זכות אליו).
2. שמור את הקובץ בשם **`sound.mp3`** (או `sound.wav` / `sound.ogg`).
3. הכנס אותו בדיוק לכאן: `proximity-sound/app/src/main/res/raw/sound.mp3`.
   (מחק קודם את `sound.wav` הקיים, או החלף אותו — שם הקובץ צריך להיות `sound` בלי תוספות).
4. דחוף ל-branch ב-GitHub — ה-Workflow `Build Proximity-Sound APK` יבנה APK חדש אוטומטית.

> כללי שמות ל-`res/raw`: רק אותיות קטנות, ספרות וקו תחתון. אין רווחים, אין אותיות גדולות.

## איך מקבלים את ה-APK

### אופציה 1 — בנייה ב-GitHub Actions (מומלץ)

ברגע שאתה דוחף את התיקייה `proximity-sound/` ל-branch `claude/hand-tracking-camera-qY4kI` או `claude/hand-tracking-camera-iKo1K`, ה-workflow `Build Proximity-Sound APK` רץ אוטומטית ויוצר Release עם ה-APK ב:

```
https://github.com/lielisraeli55/lielisraeli1/releases
```

חפש tag בשם `proximity-<run-number>` והורד את `proximity-sound-<run-number>.apk` ישירות לטלפון.

### אופציה 2 — בנייה מקומית

```bash
cd proximity-sound
gradle wrapper --gradle-version 8.7   # רק בפעם הראשונה
./gradlew assembleDebug
# APK יהיה ב:
# proximity-sound/app/build/outputs/apk/debug/app-debug.apk
```

נדרש: JDK 17, Android SDK עם platform-34.

## התקנה בטלפון

1. הורד את ה-APK לטלפון.
2. אפשר התקנה ממקור לא ידוע (Settings → Apps → Special access → Install unknown apps).
3. פתח את ה-APK והתקן.
4. הפעל את "קרבה לסאונד" וקרב את היד לחלק העליון של חזית המסך — תשמע את הסאונד.

## Troubleshooting

* **"אין חיישן קרבה במכשיר הזה"** — חלק מהמכשירים החדשים (במיוחד עם מסך Under-Display) הסירו את החיישן הפיזי. במקרה כזה האפליקציה לא תוכל לעבוד.
* **המרחק לא משתנה, רק קופץ בין 0 ל-`max`** — זה תקין; רוב חיישני הקרבה במובייל הם בינאריים.
* **לא נשמע סאונד** — ודא שהקובץ שלך ב-`res/raw/sound.*` הוא MP3/WAV/OGG תקני, ושהשם הוא בדיוק `sound` (אותיות קטנות, בלי רווחים).

## מבנה הפרויקט

```
proximity-sound/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/lielisraeli/proximitysound/MainActivity.kt
        └── res/
            ├── layout/activity_main.xml
            ├── values/{strings,colors,themes}.xml
            ├── drawable/ic_launcher_{background,foreground}.xml
            ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
            └── raw/sound.wav   ← החלף בקובץ שלך
```
