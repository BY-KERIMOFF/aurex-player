# 18+ Məzmun və PIN Kod İdarəetməsinin Sadələşdirilməsi

Bu plan 18+ kanalların hər zaman görünməsini təmin edir və standart PIN kodu "2266" olaraq təyin edir.

## User Review Required

> [!IMPORTANT]
> Bu dəyişiklikdən sonra:
> 1. "Ayarlar" bölməsindəki "Böyüklər üçün məzmunu göstər" seçimi ləğv ediləcək. 18+ kateqoriyalar hər zaman siyahıda görünəcək.
> 2. Standart (ilk) PIN kod **2266** olacaq.
> 3. 18+ kateqoriyaya daxil olmaq istədikdə hər zaman PIN soruşulacaq.

## Proposed Changes

### [Utilities]

#### [MODIFY] [PinDialog.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/utils/PinDialog.java)
- PIN yoxlanışı üçün `SharedPreferences` istifadə olunacaq.
- Standart PIN dəyəri "0000"-dan **"2266"**-ya dəyişdiriləcək.

### [Activities]

#### [MODIFY] [SettingsActivity.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/SettingsActivity.java)
- "Böyüklər üçün məzmunu göstər" (`cbShowAdult`) seçimi UI-dan və koddan silinəcək.
- PIN dəyişdirmə hissəsində köhnə "0000" xəbərdarlığı "2266" ilə əvəz olunacaq.

#### [MODIFY] [LiveTvActivity.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/LiveTvActivity.java)
- Kanal və kateqoriya yükləmə zamanı tətbiq olunan 18+ süzgəcləri ləğv ediləcək. Bütün kateqoriyalar hər zaman görünəcək.
- Kateqoriya klikləndikdə `PinDialog` vasitəsilə qorunma davam edəcək.

### [Web Application (Smart TV)]

#### [MODIFY] [msx/app.js](file:///home/by-kerimoff/Belgeler/by-kerimoff/msx/app.js)
- Standart PIN "2266" olaraq təyin ediləcək.
- Kateqoriya süzgəcləri (Adult filter) ləğv edilərək hamısının görünməsi təmin olunacaq.

## Verification Plan

### Manual Verification
1. Tətbiqi açıb "Live TV" bölməsinə daxil olmaq.
2. 18+ kateqoriyanın siyahıda olduğunu yoxlamaq (heç bir ayar etmədən).
3. Həmin kateqoriyaya klikləmək -> PIN pəncərəsinin açıldığını görmək.
4. **2266** yazaraq girişin uğurlu olduğunu yoxlamaq.
5. Ayarlara daxil olub PIN-i dəyişmək və yeni PIN-lə girişi yoxlamaq.
