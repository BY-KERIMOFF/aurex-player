# "Aurex Player Smart TV" (Final Gold - v192) Tətbiq Planı

Bu plan Smart TV versiyasını (MediaStation X) Android APK-nın **mütləq kopyasına** çevirəcək son toxunuşları və qabaqcıl idarəetmə funksiyalarını əhatə edir.

## Proposed Changes

### [Web Application Components]

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
1.  **Rəqəmlə Kanal Seçimi:**
    - Pultun 0-9 rəqəm düymələrini tanıyan `numericInput` sistemi.
    - Rəqəmlər daxil edildikdə ekranda balaca bildiriş çıxacaq və 1.5 saniyə sonra həmin nömrəli kanal avtomatik açılacaq.
2.  **Ekran Formatı (Aspect Ratio):**
    - Pultun **Sarı** düyməsi ilə video formatını dəyişmək: `FIT`, `FILL` və `ZOOM`.
    - Hər dəyişiklikdə ekranda cari rejim barədə bildiriş çıxacaq.
3.  **Radio Fix & Geniş Siyahı:**
    - Radio stansiyalarının səsləndirilməsi üçün `Audio` obyekti təkmilləşdiriləcək.
    - Siyahıda AZ, TR və RU radioları tam şəkildə kopyalanacaq.
4.  **EPG & Arxiv Real Data:**
    - `epg.pw` vasitəsilə kanalların proqram məlumatlarının çəkilməsi.
    - Arxiv dəstəyi olan kanallarda pleyer daxilində "Geri" verilişlərin siyahısını göstərmək.
5.  **Geri (Back) Düyməsi Məntiqi:**
    - Pleyer açıqdırsa: 1-ci basış -> Kanal siyahısı açılır. 2-ci basış -> Dashboard-a qayıdır.

#### [MODIFY] [msx/style.css](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/style.css)
- **Aspect Ratio Classes:** Video elementini müxtəlif formatlarda göstərmək üçün CSS sinifləri (`object-fit`).
- **Numeric Overlay:** Ekranda daxil edilən rəqəmlərin görünməsi üçün şüşə effektli bildiriş paneli.

#### [MODIFY] [msx/index.html](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/index.html)
- Rəqəm girişi və format bildirişləri üçün lazımi HTML elementləri əlavə ediləcək.

### [Gradle Configuration]
- `versionCode` **192**, `versionName` **"3.9.0"** olaraq yenilənəcək.

## Verification Plan

### Manual Verification
1.  **Rəqəmlər:** Pultda "1" və "2" düymələrini sıxıb 12-ci kanalın açıldığını yoxlamaq.
2.  **Sarı Düymə:** Sarı düymə ilə videonun ölçüsünün dəyişdiyini təsdiqləmək.
3.  **EPG:** Kanalların altında real veriliş adlarının göründüyünü yoxlamaq.
4.  **Geri Düyməsi:** Pleyer daxilində Geri basanda əvvəlcə kanal siyahısının gəldiyini yoxlamaq.
