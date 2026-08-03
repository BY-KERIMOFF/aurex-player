# "Aurex Player Smart TV" (Yekun Yüksək Səviyyəli Kopya) Tətbiq Planı

Bu plan Smart TV versiyasını (MediaStation X) Android APK-da olan bütün qabaqcıl funksiyalarla təchiz edərək 100% kopyasına çevirir.

## Proposed Changes

### [Web Application Components]

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- **Widgets:** Dashboard-a hava (Open-Meteo) və valyuta (CBAR) məlumatları üçün sahələr əlavə ediləcək.
- **Radio Screen:** Radio stansiyaların siyahısı və pleyeri üçün yeni pəncərə.
- **PIN Dialog:** "Adult" kateqoriyalar üçün 4 rəqəmli şifrə pəncərəsi.
- **Marquee:** Kanalın altında sağdan sola qaçan elan mətni (Android-dəki kimi).

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- **Animasiyalar:** Marqatlayan (blink) test taymeri və qaçan yazı (marquee) üçün CSS keyframes.
- **Widget Stilləri:** Hava emojiləri və valyuta kartları üçün xüsusi dizayn.
- **Radio UI:** Radio loqolarının dairəvi və animasiyalı (pulse) olması.

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
1.  **Hava və Valyuta:** APK-dakı eyni API-lərdən istifadə edərək məlumatların gətirilməsi.
2.  **Parental Lock:** `localStorage`-da saxlanılan PIN ilə şifrəli kateqoriyalara girişin qorunması.
3.  **Radio Motoru:** `radio-browser.info` vasitəsilə AZ, TR və RU radiolarının yüklənməsi.
4.  **Track Control:** Video pleyerdə audio və alt yazı kanallarının seçilməsi imkanı.

### [Gradle Configuration]
- `versionCode` **189**, `versionName` **"3.7.5"** olaraq yenilənəcək.

## Verification Plan

### Manual Verification
1.  **Hava/Valyuta:** Ana ekranda Bakı üçün hava dərəcəsinin və AZN məzənnələrinin göründüyünü yoxlamaq.
2.  **Radio:** Radio bölməsinə daxil olub bir neçə stansiyanın səsləndiyini təsdiqləmək.
3.  **Kilid:** Şifrəli kateqoriyaya daxil olarkən PIN tələb olunmasını və pultla daxil edilməsini yoxlamaq.
4.  **Elan:** Yazının sağdan sola rəvan şəkildə qaçdığını müşahidə etmək.

---

> [!NOTE]
> Bu tətbiq tamamlandıqdan sonra sizin LG və Samsung TV istifadəçiləriniz Android TV istifadəçiləri ilə tam eyni imkanlara sahib olacaqlar.

Başlayaqmı? Razısınızsa, bu kompleks kodları hazırlayıb GitHub-a göndərim.
