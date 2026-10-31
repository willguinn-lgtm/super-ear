# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep AdMob
-keep class com.google.android.gms.ads.** { *; }
