# Manual DNS (DoH) Hesabatı

İstifadəçilərin provayder bloklamalarını daha effektiv şəkildə aşa bilməsi üçün "Manual DNS" funksiyası əlavə edildi.

## 1. Manual DNS (DoH) Dəstəyi
- Tənzimləmələr (Settings) menyusuna **"Manual DNS (DoH URL)"** seçimi əlavə olundu.
- İstifadəçilər artıq öz istədikləri DNS over HTTPS (DoH) ünvanlarını (məsələn: `https://dns.adguard.com/dns-query`) daxil edə bilərlər.

## 2. İstifadəçi İnterfeysi
- DNS seçimləri panelində yeni variant yaradıldı.
- "Manual DNS" seçildikdə URL daxil etmək üçün `EditText` sahəsi avtomatik olaraq görünür.
- Daxil edilən URL `SharedPreferences` vasitəsilə yadda saxlanılır və tətbiq hər dəfə açılanda avtomatik tətbiq olunur.

## 3. Şəbəkə Təhlükəsizliyi
- `NetworkUtils` sinfi təkmilləşdirildi ki, istifadəçinin daxil etdiyi custom DoH URL-ni OkHttp və ExoPlayer sorğularına inteqrasiya edə bilsin.
- Hər hansı xətalı URL daxil edildikdə, sistem avtomatik olaraq standart DNS-ə qayıdaraq tətbiqin işini dayandırmasına mane olur.

## Yoxlama Nəticəsi
- Layihə uğurla build olundu (`assembleDebug` uğurla tamamlandı).
- Manual DNS funksiyası həm UI, həm də backend səviyyəsində tam işlək vəziyyətdədir.
