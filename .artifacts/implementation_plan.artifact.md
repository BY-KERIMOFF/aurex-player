# Uzun Elan Mətnlərinin (Marquee) Tam Görünməsi və Sürət Tənzimlənməsi

Bu plan həm Android APK-da, həm də Smart TV versiyasında çox uzun olan elan mətnlərinin (tikerlərin) ekranda rəvan və oxunaqlı şəkildə qaçmasını təmin edir.

## Proposed Changes

### [Player Activity - Android]

#### [MODIFY] [PlayerActivity.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/PlayerActivity.java)
- `startAnnouncementAnimation()` metodu yenilənəcək.
- Sabit `25000ms` (25 saniyə) müddəti əvəzinə, müddət mətnin uzunluğuna mütənasib olaraq hesablanacaq.
- **Düstur:** `duration = (screenWidth + textWidth) * 15` (hər piksel üçün 15ms). Bu, mətn nə qədər uzun olsa da, eyni sürətlə hərəkət etməsini təmin edir.

### [Web Application (MSX) - Smart TV]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- `player-view` daxilinə elan mətni üçün konteyner (`#announcement-bar`) yenidən əlavə ediləcək.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- Elan barı üçün Android-dəki ilə eyni dizayn (Yarım-şəffaf qara fon, qızılı yazı) tətbiq olunacaq.
- Sabit animasiya müddəti ləğv ediləcək, çünki o JS vasitəsilə dinamik təyin olunacaq.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
- Serverdən uzun elan mətni gəldikdə, tətbiq mətnin uzunluğunu ölçəcək və CSS `animation-duration` dəyərini ona uyğun təyin edəcək.
- Bu, çox uzun reklam mətnlərinin TV-də çox sürətli qaçmasının qarşısını alacaq.

### [Gradle Configuration]
- `versionCode` **199**, `versionName` **"5.2.0"** olaraq yenilənəcək.

## Verification Plan
1. Serverdəki JSON-da çox uzun (məsələn, 500 simvollu) bir `announcement` yazmaq.
2. APK-da yazının hərəkət sürətinin normal (çox sürətli deyil) olduğunu yoxlamaq.
3. Smart TV-də (MediaStation X) eyni uzunluqlu yazının tam şəkildə göründüyünü və oxunduğunu təsdiqləmək.
