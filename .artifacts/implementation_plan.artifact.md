# "Aurex Player Smart TV" (Premium Final Kopya - v190) Tətbiq Planı

Bu plan Smart TV versiyasını Android APK-nın 100% tam və premium kopyasına çevirir. Buraya EPG (Proqram rəhbəri), Arxiv (Catchup), genişləndirilmiş Radio və fərdiləşdirilmiş rənglər daxildir.

## Proposed Changes

### [Web Application Components]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- **EPG Container:** Kanal siyahısında hər bir kanalın altında cari verilişin adını göstərən sahə əlavə ediləcək.
- **Archive Sidebar:** Ötürülmüş verilişlərin siyahısını göstərmək üçün yeni yan panel.
- **Settings Screen:** Kanal siyahısı görünüşünü (Classic/Compact) dəyişmək üçün tənzimləmələr bölməsi.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- **Dynamic Marquee Color:** Elan yazısının rəngi serverdən gələn koda görə dəyişəcək.
- **Compact View Styles:** Kanalların daha kiçik və sıx görünməsi üçün yeni CSS sinifləri.
- **EPG Progress:** Kanal siyahısında verilişin nə qədərinin keçdiyini göstərən nazik tərəqqi çubuğu.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
1.  **EPG Motoru:** `epg.pw` və ya M3U-dakı `tvg-url` vasitəsilə XMLTV fayllarını çəkib emal edəcək.
2.  **Arxiv (Catchup) Dəstəyi:**
    - Xtream üçün `timeshift` URL-lərinin yaradılması.
    - M3U üçün `${start}` və `${offset}` parametrlərinin dəstəklənməsi.
3.  **Geniş Radio Siyahısı:** AZ, TR və RU radiolarının bir siyahıda birləşdirilməsi.
4.  **UI Settings:** İstifadəçinin seçdiyi görünüş rejiminin yadda saxlanılması.

### [Gradle Configuration]
- `versionCode` **190**, `versionName` **"3.8.0"** olaraq yenilənəcək.

## Verification Plan

### Manual Verification
1.  **EPG:** Kanal siyahısında kanalların altında cari verilişin adının və vaxtının göründüyünü yoxlamaq.
2.  **Arxiv:** Pleyer daxilində "Arxiv" menyusuna girib ötürülmüş bir verilişi açmaq.
3.  **Radio:** Siyahıda Azərbaycan, Türkiyə və Rusiya radiolarının hamısının olduğunu yoxlamaq.
4.  **Görünüş:** Ayarlardan "Kompakt" rejimi seçdikdə siyahının dəyişdiyini təsdiqləmək.
5.  **Rəng:** Serverdə `announcementColor` dəyişdirildikdə TV-dəki yazının rənginin dəyişdiyini görmək.
