# Premium Splash Screen və Naviqasiya Düzəlişi Planı

Bu plan tətbiqin açılış ekranını (Splash) loqosuz, sadəcə estetik "AUREX PLAYER" yazısı ilə premium hala gətirməyi və Dashboard-un görünməməsi problemini həll etməyi hədəfləyir. Həmçinin istifadəçinin istəyi ilə "Auto-start" funksiyası bərpa olunacaq.

## User Review Required

> [!IMPORTANT]
> **Dashboard Bərpası**: Açılış ekranındakı yüksək `elevation` ləğv ediləcək. Bu, bəzi cihazlarda proqramın "qara ekranda" qalmasına səbəb olurdu.
> **Auto-start**: Tətbiq açılan kimi ən son baxılan kanala keçid funksiyası yenidən aktiv ediləcək.

## Proposed Changes

### [Layout Təkmilləşdirilməsi]

#### [MODIFY] [activity_main.xml](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/res/layout/activity_main.xml)
- `loadingLayout` hissəsindən `elevation="1000dp"` silinəcək.
- `loadingLayout` tam qara fonda mərkəzləşmiş "AUREX PLAYER" yazısı ilə saxlanılacaq, lakin vizual olaraq digər elementləri örtməyəcək şəkildə idarə olunacaq.

### [Kod və Naviqasiya]

#### [MODIFY] [MainActivity.kt](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/MainActivity.kt)
- `handleAutoStart()` funksiyasının çağırışı bərpa olunacaq.
- `showDashboard` metodunda `loadingLayout`-un tam gizlədilməsi və `dashboardLayout`-un önə gətirilməsi təmin ediləcək.
- `onResume` məntiqi gücləndiriləcək ki, kanaldan qayıdanda Dashboard həmişə görünsün.

## Verification Plan

### Automated Tests
- Layihənin uğurla `assembleRelease` olunmasını yoxlamaq.

### Manual Verification
- Tətbiqi açanda mərkəzdə "AUREX PLAYER" yazısının gəldiyini, sonra tətbiqin avtomatik son kanala keçdiyini yoxlamaq.
- Kanaldan "Geri" düyməsi ilə qayıtdıqda Dashboard-un (Canlı TV, Ayarlar və s.) dərhal göründüyünü təsdiqləmək.
