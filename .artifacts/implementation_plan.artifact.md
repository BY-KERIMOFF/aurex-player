# "Aurex Player" İnternet Sürət Testi (Speed Test) İnteqrasiyası

Bu plan həm Android APK-da, həm də Smart TV (MSX) versiyasında istifadəçilərin internet sürətini birbaşa tətbiq daxilində yoxlaya bilməsi üçün "Speed Test" funksiyasının əlavə edilməsini əhatə edir.

## Proposed Changes

### [Android App - Studio]

#### [MODIFY] [res/layout/activity_settings.xml](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/res/layout/activity_settings.xml)
- Ayarlar menyusunun sonuna "İnternet Sürət Testi" adlı yeni bir bölmə əlavə ediləcək.
- Sürəti göstərən vizual indikator (Mbps) və "TESTİ BAŞLAT" düyməsi yerləşdiriləcək.

#### [MODIFY] [utils/NetworkUtils.kt](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/utils/NetworkUtils.kt)
- `performSpeedTest` funksiyası əlavə olunacaq.
- Sabit bir faylı (məs: 10MB test file) yükləyərək real vaxt rejimində sürəti hesablayacaq.

#### [MODIFY] [SettingsActivity.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/SettingsActivity.java)
- Sürət testi düyməsinin məntiqi yazılacaq və nəticələr ekranda göstəriləcək.

### [Web Application (MSX) - Smart TV]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- Ayarlar ekranına "Sürət Testi" bölməsi və nəticə pəncərəsi əlavə ediləcək.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- Sürət göstəricisi üçün qızılı rəngli animasiyalı dairəvi tərəqqi çubuğu (gauge) dizaynı.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
- JavaScript `fetch` API vasitəsilə kiçik bir data paketini çəkərək TV-nin qoşulma sürətini (Mbps) hesablayan məntiq.

### [Gradle Configuration]
- `versionCode` **204**, `versionName` **"6.5.0"** (Network Master) olaraq yenilənəcək.

## Verification Plan

### Manual Verification
1. Ayarlar menyusuna daxil olub "Speed Test" bölməsini tapmaq.
2. "TESTİ BAŞLAT" düyməsini sıxmaq.
3. Sürətin (məs: 25.4 Mbps) düzgün hesablandığını və ekranda göründüyünü yoxlamaq.
4. Eyni testi həm telefon (APK), həm də TV-də (MSX) yoxlayaraq nəticələrin doğruluğunu təsdiqləmək.
