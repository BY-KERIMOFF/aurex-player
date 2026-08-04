# "Aurex Player Smart TV" (Mütləq Final - v200) Tətbiq Planı

Bu plan Smart TV versiyasını (MediaStation X) Android APK-nın sonuncu çatışmayan funksiyaları (Son baxılan kanallar, VOD süzgəcləmə) ilə təmin edərək layihəni rəsmi olaraq yekunlaşdırır.

## Proposed Changes

### [Web Application Components]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- **Recent Channels Section:** Dashboard-a "SON BAXILAN KANALLAR" adlı yeni bir horizontal siyahı sahəsi əlavə ediləcək.
- **VOD Labels:** Filmlər və Seriallar üçün xüsusi başlıqlar və kateqoriya strukturu.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
1.  **Smart VOD Filter:**
    - M3U oxunarkən linkin sonundakı `.mp4`, `.mkv` və ya qovluq adlarına (`/movie/`, `/series/`) baxaraq kanalları avtomatik "Canlı", "Film" və "Serial" qruplarına ayıracaq.
2.  **Recent History Engine:**
    - İzlənilən kanalları `localStorage`-da `aurex_recent_tv` adı ilə yadda saxlayacaq.
    - Ana ekranda bu kanalların loqoları ilə birlikdə göstərilməsini təmin edəcək.
3.  **Radio Stream Fix:**
    - Yeni radio seçiləndə əvvəlkinin səsinin tam kəsilməsi (overlap olmaması) üçün mərkəzi `Audio` obyekti tətbiq olunacaq.

### [Gradle Configuration]
- `versionCode` **200**, `versionName` **"6.0.0"** (The Masterpiece) olaraq yenilənəcək.

## Verification Plan
1.  **Recent:** Bir neçə kanala baxıb çıxmaq -> Ana ekranda həmin kanalların siyahıya düşdüyünü yoxlamaq.
2.  **VOD:** "FİLMLƏR" bölməsinə girdikdə yalnız filmlərin (video faylların) siyahıda olduğunu təsdiqləmək.
3.  **Radio:** Bir radiodan digərinə keçəndə köhnə səsin dərhal kəsildiyini yoxlamaq.

---

> [!NOTE]
> Bu tətbiqin sonuncu (v200) yeniləməsidir. Bundan sonra layihə həm texniki, həm də vizual olaraq tam mükəmməl hala gələcək.
