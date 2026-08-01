# Neo Play TV v1.8.3 Release Hesabatı

Bu versiyada test istifadəçiləri üçün pleyer ekranında mühüm bir vizual təkmilləşdirmə aparıldı.

## Yeniliklər və Təkmilləşdirmələr

### 1. Pleyer Ekranında Daimi Test Geri Sayımı
- **Təsvir**: Artıq test rejimi aktiv olduqda, qalan vaxt pleyer ekranında (kanallara baxarkən) sağ aşağı küncdə **daimi** olaraq göstərilir.
- **İşləmə Məntiqi**:
    - OSD (aşağıdakı böyük məlumat paneli) itsə belə, test vaxtı saniyə-saniyə azalaraq ekranda qalmağa davam edir.
    - Bu sahə saat və video keyfiyyəti panelinin yanında yerləşir.
    - Vaxt bitdikdə avtomatik olaraq gizlənir və ya cihaz bloklanır.

### 2. Canlı Yenilənmə
- Pleyer daxilində xüsusi bir `Handler` quruldu ki, bu da vaxtın hər saniyə dəqiq yenilənməsini təmin edir.

## Yekun Build Məlumatları
- **Versiya**: 1.8.3 (66)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v66.apk`
- **Status**: Build finished successfully.

Artıq test müddətini izləmək üçün pultda hər hansı düyməyə basmağa ehtiyac yoxdur, o həmişə göz önündədir.
