# Neo Play TV v1.8.4 Release Hesabatı

Bu versiyada server tərəfindən böyüklər üçün nəzərdə tutulmuş (+18) məzmunun idarə edilməsi funksiyası əlavə edildi.

## Yeniliklər və Təkmilləşdirmələr

### 1. Dinamik +18 Məzmun Kontrolu
- **Təsvir**: Artıq admin panelindən (serverdən) hər bir cihaz üçün +18 kanallarının görünüb-görünməməsini tənzimləmək mümkündür.
- **İşləmə Məntiqi**:
    - Serverdən gələn `is_adult` parametri `0` olduqda, tətbiq avtomatik olaraq bütün "Adult", "XXX", "+18" və s. kimi kateqoriyaları siyahıdan gizlədir.
    - Bu həm M3U playlistləri, həm də Xtream API yayımları üçün keçərlidir.
- **Təhlükəsizlik**: Əgər serverdə bu parametr bağlıdırsa, hətta kateqoriya şifrəli olsa belə, o ümumiyyətlə siyahıda görünməyəcək.

### 2. Texniki Təfərrüatlar
- `ApiResponse` modeli yenilənərək `is_adult` sahəsini dəstəkləyir.
- `MainActivity` bu məlumatı qəbul edir və tətbiq daxilində tətbiq olunması üçün yadda saxlayır.
- `LiveTvActivity` məlumatları yükləyərkən bu parametri yoxlayır və lazım gəldikdə filtrasiya aparır.

## Yekun Build Məlumatları
- **Versiya**: 1.8.4 (67)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v67.apk`
- **Status**: Build finished successfully.

Tətbiq artıq server tərəfindən daha dərindən idarə edilə bilir.
