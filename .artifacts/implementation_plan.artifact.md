# Kanal Siyahısı Yüklənmə Xətalarının Bildirilməsi

Bu plan Xtream və ya M3U kanal siyahıları yüklənərkən yaranan problemlər (şəbəkə xətası, boş siyahı və s.) barədə istifadəçiyə vizual məlumat verilməsini təmin edir.

## Proposed Changes

### [Activities]

#### [MODIFY] [LiveTvActivity.java](file:///home/by-kerimoff/Belgeler/by-kerimoff/app/src/main/java/com/bykerimoff/player/LiveTvActivity.java)

1.  **M3U Yükləmə Xətaları:**
    - `loadM3UFromUrl` daxilində serverdən xətalı cavab gəldikdə və ya şəbəkə bağlantısı kəsildikdə `Toast.makeText` vasitəsilə "M3U yüklənmədi" xəbərdarlığı göstəriləcək.
2.  **Xtream Yükləmə Xətaları:**
    - `loadXtreamDataInternal` və onun geri çağırışlarında (onFailure) server xətaları barədə daha dəqiq bildirişlər əlavə ediləcək.
3.  **Boş Siyahı Yoxlanışı:**
    - `processLoadedChannels` və `processXtreamChannels` metodlarının sonunda əgər heç bir kanal tapılmayıbsa, ekranda "Kanal siyahısı boşdur" yazısı çıxacaq.

## Verification Plan

### Manual Verification
- Səhv bir M3U linki daxil edib "Məlumatları Yenilə" sıxmaq -> "M3U yüklənmədi" mesajını görmək.
- İnterneti söndürüb siyahını yeniləməyə çalışmaq -> "Bağlantı xətası" mesajını görmək.
- Boş bir Xtream hesabı ilə daxil olmaq -> "Siyahı boşdur" mesajını görmək.
