# Neo Play TV v1.9.3 Release Hesabatı

Bu versiyada tətbiqin server tərəfindən idarə edilməsi imkanları genişləndirildi. Artıq admin panelindən tətbiqi həm hamı üçün, həm də fərdi olaraq bağlamaq mümkündür.

## Yeniliklər və Təkmilləşdirmələr

### 1. Qlobal Tətbiq Bağlanma Sistemi
- **Təsvir**: Əgər serverdə ümumi tətbiq statusu söndürülərsə (`apk_global_status = 0`), tətbiqi açan hər bir istifadəçi "Tətbiq müvəqqəti olaraq bağlanıb" mesajını görəcək.
- **Məqsəd**: Server tərəfdə texniki işlər getdikdə və ya tətbiqin istifadəsini hamı üçün dayandırmaq lazım gəldikdə istifadə olunur.

### 2. Fərdi Cihaz Bağlanması
- **Təsvir**: Müəyyən bir cihaz (MAC ünvanı) üçün girişi serverdən birbaşa bağlamaq olar (`app_active = 0`). Bu halda həmin istifadəçi "Tətbiq bağlanıb" xəbərdarlığını görəcək.

### 3. Tətbiq İstifadə Müddəti (App Expiry)
- **Təsvir**: Artıq hər bir cihaz üçün tətbiqin özünün ayrıca son istifadə tarixi təyin edilə bilər (`app_expire_date`). Bu tarix keçdikdə tətbiq avtomatik olaraq bağlanacaq.

### 4. Təkmilləşdirilmiş Xəta Mesajları
- Serverdən gələn bütün bloklama və bağlanma mesajları (`message` və `detail`) tətbiq ekranında aydın və Azərbaycan dilində əks olunur.

## Yekun Build Məlumatları
- **Versiya**: 1.9.3 (72)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v72.apk`
- **Status**: Build finished successfully.

Tətbiq artıq həm qlobal, həm də fərdi səviyyədə tam nəzarət altındadır. 🔒📉
