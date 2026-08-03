# "Aurex Player Smart TV" (Səs Paneli və Real EPG) Tətbiq Planı

Bu plan Smart TV versiyasını (MediaStation X) Android APK-dakı vizual səs səviyyəsi və real proqram rəhbəri (EPG) funksiyaları ilə təkmilləşdirir.

## Proposed Changes

### [Web Application Components]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- **Volume Overlay:** Ekranda səs artırıb-azaldanda görünəcək qızılı rəngli yeni bir panel (`#volume-overlay`) əlavə ediləcək.
- **EPG Placeholders:** Kanal siyahısında və pleyer daxilində real EPG məlumatlarının oturacağı sahələr dəqiqləşdiriləcək.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- **Volume UI Styles:** Android APK-dakı səs paneli ilə eyni dizayn (Qızılı tərəqqi çubuğu, şüşə effekti).
- **EPG Text Glow:** Proqram adlarının qaranlıq fon üzərində daha aydın görünməsi üçün kölgə və vurğu stilləri.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
1.  **Custom Volume Logic:**
    - Pultun `AudioVolumeUp` və `AudioVolumeDown` düymələrini tutacaq.
    - Video pleyerin səsini dəyişəcək və ekranda xüsusi qızılı səs panelini göstərəcək.
    - Panel 3 saniyə hərəkətsizlikdən sonra avtomatik itəcək.
2.  **Real EPG Parser:**
    - `https://epg.pw/xmltv/feed/az.xml` mənbəsindən məlumatları `DOMParser` vasitəsilə oxuyacaq.
    - Hər bir kanalın `tvg-id` dəyərinə uyğun olaraq hal-hazırda hansı verilişin getdiyini tapacaq.
    - Verilişin başlama və bitmə vaxtına görə tərəqqi çubuğunu (`progress bar`) avtomatik yeniləyəcək.

### [Gradle Configuration]
- `versionCode` **195**, `versionName` **"4.1.0"** (Ultimate Edition) olaraq yenilənəcək.

## Verification Plan

### Manual Verification
1.  **Səs Paneli:** Pultda səs düymələrini sıxdıqda ekranda qızılı rəngli çubuğun çıxdığını və səs səviyyəsinin (0-100%) düzgün dəyişdiyini yoxlamaq.
2.  **EPG Data:** Kanalları gəzərkən "Yayım məlumatı yoxdur" yerinə real veriliş adlarının (məs: "Hədəf", "Kino") gəldiyini təsdiqləmək.
3.  **EPG Progress:** Verilişin nə qədərinin getdiyini göstərən yaşıl/qızılı çubuğun pleyer menyusunda (OSD) hərəkət etdiyini görmək.
