# "Aurex Player" (Mütləq Kopya və 18+ Fix - v197) Tətbiq Planı

Bu plan həm Android APK-dakı (+18) kanalların görünməməsi problemini həll edir, həm də Smart TV versiyasını APK-nın **100% vizual və funksional kopyasına** (Pixel-Perfect Mirror) çevirir.

## User Review Required

> [!IMPORTANT]
> **18+ Kanallar:** İndi həm APK-da, həm də Smart TV-də "Ayarlar" bölməsinə "Böyüklər üçün məzmunu göstər" seçimi əlavə ediləcək. Bu seçim yalnız düzgün PIN kod daxil edildikdə aktivləşə bilər. Beləliklə, kanalları serverdən asılı olmayaraq (əgər icazə varsa) gizlədib-açmaq mümkün olacaq.

## Proposed Changes

### [Android Studio - Layouts & Logic]

#### [MODIFY] [SettingsActivity.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/SettingsActivity.java)
- "Böyüklər üçün məzmunu göstər" (`cbShowAdult`) adlı yeni bir `CheckBox` (və ya Switch) əlavə ediləcək.
- Bu seçim klikləndikdə istifadəçidən PIN tələb olunacaq.

#### [MODIFY] [LiveTvActivity.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/LiveTvActivity.java)
- Kanalları süzgəcləyərkən yalnız serverdən gələn `is_adult_enabled` yox, həm də istifadəçinin ayarlarda verdiyi `show_adult_content` icazəsi yoxlanılacaq.

### [Web Application (MSX) - Absolute Mirror]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- **Dashboard:** `activity_main.xml`-dəki bütün vidjetlərin (Hava, Saat, Tarix) və bölmələrin (Resume, Currency) dəqiq yerləşməsi.
- **Login:** Giriş ekranı APK-dakı "MAC Adresi" pəncərəsi ilə 1:1 eyniləşdiriləcək.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- **Dizayn Mirror:** Android Studio-dakı bütün rəng kodları (`#FFD700`, `#0A0A0A`, `#121212`) və `dp` ölçüləri dəqiq tətbiq olunacaq.
- **Fontlar:** APK-dakı eyni font ailələri və ölçüləri.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
- **Adult Filter:** APK-da tətbiq etdiyimiz 18+ gizlətmə/açma məntiqi bura da əlavə ediləcək.
- **Dashboard Search:** APK-dakı kimi dərhal klaviaturanı açan axtarış motoru.

### [Gradle Configuration]
- `versionCode` **197**, `versionName` **"5.0.0"** (The Absolute Mirror) olaraq yenilənəcək.

## Verification Plan

### Manual Verification
1.  **APK 18+ Testi:** Ayarlardan "Böyüklər üçün məzmun"u açıb PIN yazdıqdan sonra kanalların gəldiyini yoxlamaq.
2.  **Smart TV Mirror Testi:** TV-dəki pleyeri APK ilə yan-yana qoyub heç bir fərq (rəng, ölçü, yerləşmə) olmadığını təsdiqləmək.
3.  **Hər iki tərəfdə EPG:** Canlı yayım zamanı real proqram məlumatlarının hər iki tərəfdə eyni saniyədə yeniləndiyini yoxlamaq.
