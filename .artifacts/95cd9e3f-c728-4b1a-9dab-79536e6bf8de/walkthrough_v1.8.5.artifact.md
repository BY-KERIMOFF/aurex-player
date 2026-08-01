# Neo Play TV v1.8.5 Release Hesabatı

Bu versiyada test rejimi zamanı pleyer ekranında vaxtın görünməsi tam stabilləşdirildi.

## Yeniliklər və Təkmilləşdirmələr

### 1. Müstəqil Test Geri Sayımı
- **Təsvir**: Artıq test vaxtı pleyer ekranında (kanallara baxarkən) digər heç bir paneldən (saat, keyfiyyət, OSD) asılı olmayaraq, həmişə sağ aşağı küncdə görünür.
- **İşləmə Məntiqi**:
    - `tvPlayerTestCountdownStatic` elementi ayrı bir sahəyə keçirildi.
    - Əgər test rejimi aktivdirsə, bu sahə həmişə ekranda qalacaq və saniyə-saniyə azalacaq.
    - OSD paneli və ya saat paneli itdikdə belə, test vaxtı görünməyə davam edəcək.

## Yekun Build Məlumatları
- **Versiya**: 1.8.5 (68)
- **Fayl**: `app/build/outputs/apk/release/neoplay_v68.apk`
- **Status**: Build finished successfully.

Artıq test istifadəçiləri qalan vaxtlarını heç bir maneə olmadan izləyə biləcəklər.
