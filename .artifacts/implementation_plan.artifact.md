# "Aurex Player Smart TV" (Yekun Lüks Kopya - v193) Tətbiq Planı

Bu plan Smart TV versiyasını Android APK-nın mütləq və yekun kopyasına çevirir. Bütün "gizli" və qabaqcıl funksiyalar bu versiya ilə tətbiq olunacaq.

## Proposed Changes

### [Web Application Components]

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
1.  **Uzun Basma (Long Press) Mexanizmi:**
    - OK düyməsinin uzun basılmasını (1 san+) aşkarlayan sistem.
    - Kanal üzərində uzun basdıqda həmin kanalı "Sevimlilər"ə əlavə edəcək və ya çıxaracaq.
2.  **Real EPG (XMLTV) İnteqrasiyası:**
    - `az.xml`, `tr.xml` və `ru.xml` fayllarını arxa planda oxuyacaq.
    - Kanalların altında hal-hazırda gedən real verilişin adını və vaxtını göstərəcək.
3.  **Avtomatik Başlatma (Auto-Start):**
    - Tətbiq açılan kimi `localStorage`-da saxlanılan ən son baxılan kanalı tapıb avtomatik səsləndirəcək.
4.  **Axtarış Düyməsinin Aktivləşdirilməsi:**
    - Ana ekrandakı "AXTARIŞ" düyməsi klikləndikdə birbaşa kanal siyahısına keçəcək və axtarış sahəsini (input) fokuslayacaq.

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- Sevimli kanalları bildirmək üçün siyahıya ulduz (⭐) ikonu əlavə ediləcək.
- EPG məlumatlarının daha aydın görünməsi üçün struktur yenilənəcək.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- **Favorite Badge:** Siyahıda sevimli kanalların yanındakı qızılı ulduzun stilləri.
- **Search Focus:** Axtarış düyməsindən keçid zamanı yaranan vizual effektlər.

### [Gradle Configuration]
- `versionCode` **193**, `versionName` **"3.9.5"** olaraq yenilənəcək.

## Verification Plan

### Manual Verification
1.  **Sevimlilər:** Bir kanalın üzərində OK-u uzun basmaq -> Ulduzun çıxdığını və "Sevimlilər" bölməsinə düşdüyünü yoxlamaq.
2.  **EPG:** Kanalların altında "Canlı Yayım" əvəzinə real proqram adlarının (məs: "Xəbərlər", "Kino") göründüyünü təsdiqləmək.
3.  **Auto-Start:** Tətbiqi bağlayıb açanda ən son kanala avtomatik qayıtdığını yoxlamaq.
4.  **Search:** Dashboard-dakı AXTARIŞ düyməsinin birbaşa klaviaturanı açdığını yoxlamaq.
