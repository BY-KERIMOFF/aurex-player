# Neo Play TV v1.5.1 Release Hesabatı

Bu versiyada fokus kilidlənməsi və səs problemləri üzərində yekun düzəlişlər aparıldı.

## Yeniliklər və Təkmilləşdirmələr

### 1. Sərt Fokus Kilidlənməsi (Strict Focus Locking)
- `activity_live_tv.xml` səviyyəsində bütün naviqasiya cəhdləri kilidləndi. Artıq Android sistemi sürətli hərəkət zamanı fokusu siyahıdan kənar başqa elementlərə (məsələn, axtarış sahəsinə və ya kateqoriyalara) avtomatik ata bilməyəcək.
- **Dairəvi Dövr**: Siyahının sonunda aşağı basdıqda həmişə 1-ci elementə, əvvəlində yuxarı basdıqda isə sonuncu elementə qayıdış tam stabil hala gətirildi (həm List, həm də Grid rejimində).

### 2. Səs Problemi (Azad Azərbaycan HLS)
- TS və HLS yayım axınları üçün səs treklərinin aşkarlanması daha da təkmilləşdirildi.
- Bildirilən spesifik linkdə səsin çıxması üçün pleyer konfiqurasiyası yeniləndi.

### 3. Digər Təkmilləşdirmələr
- Fokusun verilməsi prosesindəki gecikmələr minimuma endirildi (50ms), bu da naviqasiyanın daha "canlı" hiss olunmasını təmin edir.

## Yekun Build Məlumatları
- **Versiya**: 1.5.1 (51)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v51.apk`
- **Status**: Build finished successfully.

Artıq naviqasiya tamamilə stabil və "qapalı" dövrə daxilindədir.
