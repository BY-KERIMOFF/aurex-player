# Neo Play TV v1.7.6 Release Hesabatı

Bu versiyada Radio bölməsində istifadəçi təcrübəsini artırmaq üçün səs səviyyəsinin vizual göstəricisi əlavə edildi.

## Yeniliklər və Təkmilləşdirmələr

### 1. Radio Səs Faiz Göstəricisi
- **Təsvir**: Artıq radio dinləyərkən pultda səs düymələrini basdıqda, ekranda TV kanallarında olduğu kimi səs faizi və tərəqqi zolağı (progress bar) görünür.
- **İşləmə Məntiqi**: Səs dəyişdikdə panel avtomatik açılır və 3 saniyə sonra öz-özünə itir.

### 2. Texniki Düzəlişlər
- `RadioActivity.java` daxilində `AudioManager` və `onKeyDown` məntiqi quruldu.
- Səs səviyyəsinin oxunması və faizə çevrilməsi optimallaşdırıldı.

## Yekun Build Məlumatları
- **Versiya**: 1.7.6 (59)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v59.apk`
- **Status**: Build finished successfully.

İndi radio dinləmək həm daha rahat, həm də vizual olaraq məlumatlandırıcıdır.
