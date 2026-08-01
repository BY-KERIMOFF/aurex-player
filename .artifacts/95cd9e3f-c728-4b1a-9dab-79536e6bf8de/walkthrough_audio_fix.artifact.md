# Səs Problemlərinin Həlli Hesabatı (v1.4.8)

Bəzi kanallarda səsin çıxmaması (xüsusilə AC3, DTS, EAC3 formatlı) problemini həll etmək üçün pleyer konfiqurasiyasında ciddi təkmilləşdirmələr aparıldı.

## 1. Dekoder Uyğunluğu və Fallback
- `DefaultRenderersFactory` artıq daxili dekoderlərə (FFmpeg və s.) üstünlük verir (`EXTENSION_RENDERER_MODE_PREFER`).
- `setEnableDecoderFallback(true)` sayəsində hardware dekoderi səs formatını dəstəkləmədikdə sistem avtomatik olaraq proqram təminatı (software) dekoderinə keçid edir.

## 2. TrackSelector Optimallaşdırması
- `DefaultTrackSelector` parametrlərinə `.setTunnelingEnabled(false)` əlavə edildi. Bu, bəzi TV box-larda səsin itməsinə səbəb olan hardware "tunneling" problemini həll edir.
- `setExceedAudioConstraintsIfNecessary(true)` və `setExceedRendererCapabilitiesIfNecessary(true)` parametrləri aktiv saxlanıldı ki, pleyer ən çətin formatları belə açmağa cəhd etsin.

## 3. Mini Pleyer İnteqrasiyası
- Eyni səs təkmilləşdirmələri həm əsas pleyerdə (`PlayerActivity`), həm də LiveTV ekranındakı mini pleyerdə tətbiq edildi.

## Yekun Nəticə
- **Versiya**: 1.4.8 (48)
- **Build**: Uğurla tamamlandı.
- **APK**: `app/build/outputs/apk/release/neoplay_v48.apk`

Artıq çoxkanallı və ya nadir səs formatlı kanalların əksəriyyətində səs problemi həll olunmuşdur.
