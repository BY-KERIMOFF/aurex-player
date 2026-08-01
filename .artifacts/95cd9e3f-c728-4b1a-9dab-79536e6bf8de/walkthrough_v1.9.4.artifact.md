# Neo Play TV v1.9.4 Release Hesabatı

Bu versiyada yüksək kadr tezliyinə (50-60 FPS) malik kanalların daha rəvan və donmadan göstərilməsi üçün pleyer motorunda mühüm optimallaşdırmalar aparıldı.

## Yeniliklər və Təkmilləşdirmələr

### 1. 60 FPS Sürətli Yayım Dəstəyi
- **Təsvir**: İdman kanalları və digər yüksək keyfiyyətli yayımlarda kadr itkisinin (frame drop) qarşısı alındı.
- **Optimallaşdırma**:
    - `DefaultLoadControl` parametrləri tənzimləndi: Buferləmə həcmi artırıldı (20-60 saniyə) ki, internet dalğalanmaları yayımı dayandırmasın.
    - Sürətli kadrlar üçün `SurfaceView` render sistemi birbaşa aktivləşdirildi.
    - Videonun emalı zamanı vaxtın (time) ölçüdən (size) daha üstün tutulması təmin edildi (`prioritizeTimeOverSizeThresholds`).

### 2. Mini Pleyer Stabilliyi
- Live TV ekranındakı mini pleyer də eyni yüksək performans sazlamaları ilə yeniləndi. Artıq kanal siyahısında gəzərkən mini pleyerdəki görüntülər daha rəvan olacaq.

### 3. Texniki Parametrlər
- **Kadr Sinxronizasiyası**: Videonun səslə tam uyğunluğu və kəsilməzliyi üçün daxili sinxronizasiya bayraqları gücləndirildi.

## Yekun Build Məlumatları
- **Versiya**: 1.9.4 (73)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v73.apk`
- **Status**: Build finished successfully.

Artıq bütün kanallar, xüsusilə də yüksək sürətli idman yayımları tam rəvan və stabil şəkildə istifadəyə hazırdır. ⚽📺🚀
