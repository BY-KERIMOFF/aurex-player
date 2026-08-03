# "Aurex Player Smart TV" (Tam Kopya - v188) Tətbiq Planı

Bu plan MediaStation X tətbiqini sizin Android APK-nın **vizual və funksional tam kopyasına** çevirir. Buraya MAC adres ilə giriş, eyni rəng sxemi və bütün bölmələr daxildir.

## Proposed Changes

### [Web Application Components]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- **Login Screen:** APK-dakı "MAC Ünvanı" ekranına bənzər giriş səhifəsi.
- **Splash Screen:** Açılışda qızılı "AUREX PLAYER" yazısı olan animasiyalı ekran.
- **Dashboard:** Eyni kartlar (Canlı TV, Filmlər, Seriallar, Sevimlilər).
- **Layout:** Bütün pəncərələr Android-dəki ölçülər və yerləşmə ilə eyni olacaq.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- **Android Studio Mövzusu:** Android-dəki `@color/gold_primary` (#FFD700) və `@color/bg_main` (#0A0A0A) rənglərindən istifadə olunacaq.
- **Fokus Effektləri:** Elementin üzərinə gələndə **Sarı fon və Qara yazı** effektləri tətbiq ediləcək.
- **Kart Dizaynı:** Android-dəki kimi yuvarlaq künclər (15dp) və kölgələr.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
1.  **MAC Auth:** `http://kanal65.xyz/api.php?mac=...` vasitəsilə eyni autentifikasya sistemi.
2.  **Dinamik Menyu:** Serverdən gələn `is_vod_enabled` və `is_series_enabled` dəyərlərinə görə Filmlər və Seriallar bölmələrini gizlədib-açacaq.
3.  **HLS/M3U8:** Smart TV-nin daxili pleyeri vasitəsilə 4K və Full HD dəstəyi.
4.  **LocalStorage:** MAC adresini yadda saxlayacaq ki, hər dəfə istəməsin.

## Texniki Addımlar (İcraat)

1.  **Dizayn Yeniləməsi:** Bütün düymə və yazı rəngləri Android XML fayllarındakı rəng kodları ilə eyniləşdiriləcək.
2.  **Giriş Sistemi:** İlk açılışda istifadəçiyə MAC daxil etmək üçün virtual klaviatura dəstəkli pəncərə göstəriləcək.
3.  **Səhifələrarası Keçid:** Android-dəki "Slide" animasiyaları veb versiyaya əlavə ediləcək.

## Verification Plan

### Manual Verification
1.  Televizorda açanda birinci MAC giriş ekranının gəldiyini görmək.
2.  MAC daxil etdikdən sonra Dashboard-un tam olaraq Android APK-ya bənzədiyini (Qara/Qızılı) yoxlamaq.
3.  Kanallarda və filmlərdə sarı fon/qara yazı effektinin işləməsini təsdiqləmək.
