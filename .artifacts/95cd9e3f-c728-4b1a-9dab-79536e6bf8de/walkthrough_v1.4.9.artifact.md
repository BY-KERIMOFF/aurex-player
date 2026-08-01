# Neo Play TV v1.4.9 Release Hesabatı

Bu versiyada naviqasiya stabilliyi və səs problemləri ilə bağlı mühüm təkmilləşdirmələr aparıldı.

## Yeniliklər və Təkmilləşdirmələr

### 1. Tam Dairəvi Naviqasiya (Looping Focus)
- Kanal və Kateqoriya siyahıları artıq tam dairəvidir.
- Siyahının ən yuxarısında "Yuxarı" düyməsini basdıqda axtarış sahəsinə avtomatik keçid ləğv edildi. Bunun əvəzinə fokus siyahının ən sonuna keçir.
- Siyahının sonuna çatdıqda "Aşağı" düyməsi ilə 1-ci elementə anidən qayıdış təmin edildi.
- Naviqasiya zamanı (istər sürətli, istərsə də yavaş) fokusun siyahıdan kənara çıxması tamamilə bloklandı.

### 2. Səs Dəstəyinin Genişləndirilməsi (Audio Compatibility)
- `DefaultExtractorsFactory` sazlamalarına `FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS` əlavə edildi.
- TS (Transport Stream) axınları üçün səs treklərinin aşkarlanması və dekod edilməsi daha aqressiv hala gətirildi.
- İstifadəçinin bildirdiyi `http://46.32.176.50/azadazerbaycanhd/index.m3u8` kimi spesifik linklərdə səsin çıxması üçün pleyer konfiqurasiyası optimallaşdırıldı.

## Yekun Build Məlumatları
- **Versiya**: 1.4.9 (49)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v49.apk`
- **Status**: Build finished successfully.

Dairəvi naviqasiya sayəsində tətbiq daxilində idarəetmə daha rahat və sürətli oldu.
