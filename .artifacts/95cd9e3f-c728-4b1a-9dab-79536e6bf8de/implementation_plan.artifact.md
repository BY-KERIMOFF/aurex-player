# 50-60 FPS Sürətli Yayım Optimallaşdırılması İcra Planı

Bu plan tətbiqin yüksək kadr tezliyinə (50-60 FPS) malik kanalları daha rəvan, donmadan və gecikmədən göstərməsi üçün pleyer motorunun (ExoPlayer) optimallaşdırılmasını təmin edir.

## Təklif Olunan Dəyişikliklər

### 1. Pleyer Motoru Optimallaşdırması (`PlayerActivity.java` və `LiveTvActivity.java`)
- **Hardware Acceleration Prioriteti**: Videonun emalı üçün cihazın aparat sürətləndiricisindən (hardware decoders) daha effektiv istifadə təmin ediləcək.
- **Buferləmə Sazlamaları (`DefaultLoadControl`)**: 60 FPS yayımlar daha çox məlumat tələb etdiyi üçün bufer parametrləri tənzimlənəcək:
    - `bufferForPlaybackAfterRebufferMs` artırılacaq ki, internetdə balaca tərəddüd olsa belə yayım dərhal kəsilməsin.
- **Video Emalı Bayraqları**: `DefaultExtractorsFactory` daxilində yüksək kadr tezlikli TS yayımları üçün xüsusi bayraqlar (`FLAG_ALLOW_NON_IDR_KEYFRAMES`) daha dərindən konfiqurasiya ediləcək.

### 2. Görüntü Rəvanlığı
- **V-Sync və Frame Drop**: Pleyer kadr itkilərini (frame drops) minimuma endirmək üçün videonun sinxronizasiya məntiqi gücləndiriləcək.
- **SurfaceView**: `PlayerView` üçün ən yüksək performanslı render metodu olan `SurfaceView` istifadəsi təmin olunacaq (əgər hələ təyin olunmayıbsa).

## Yoxlama Planı
### Manual Yoxlama
- Xüsusilə "50 FPS" və ya "60 FPS" etiketli (məsələn: İdman kanalları) yayımları açıb görüntünün "kəsilməz" (smooth) olduğunu yoxlamaq.
- Sürətli hərəkət olan səhnələrdə (futbol, yarış və s.) "kasma" olub-olmadığını müşahidə etmək.
