# --- Aqressiv Kod Gizlətmə (Obfuscation) Qaydaları ---

# Kodun optimallaşdırılma səviyyəsini artır
-optimizationpasses 5
-allowaccessmodification
-overloadaggressively

# Sətir nömrələrini və mənbə fayl adlarını tamamilə sil
-renamesourcefileattribute SourceFile
-keepattributes !SourceFile,!LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Release build-də bütün Log-ları sil (Linklərin loglara düşməməsi üçün)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# --- Model və Data qorunması ---
# Retrofit və Gson istifadə etdiyimiz üçün modellər gizlədilməməlidir, yoxsa JSON-dan gələn datalar boş qalar.
-keep class com.bykerimoff.player.models.** { *; }
-keepclassmembers class com.bykerimoff.player.models.** { *; }

# API cavablarını qoru
-keep class com.bykerimoff.player.api.** { *; }

# --- Kitabxana Qaydaları ---

# Retrofit 2
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses

# Gson
-keep class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.** { *; }

# Glide (Şəkil yükləmə)
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# --- Digər Vacib Qaydalar ---

# ViewBinding üçün lazım olan klassları saxla
-keep class com.bykerimoff.player.databinding.** { *; }

# Android əməliyyat sistemi tərəfindən çağırılan metodları qoru
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Enum-ları qoru
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
