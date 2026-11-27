# Reguły R8 dla launchera Dom.

# Most JavaScript ↔ Kotlin w odtwarzaczu zwiastunów.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# Usługa ułatwień dostępu, odbiornik aktualizacji i aktywności są w manifeście (R8 je zachowuje),
# ale na wszelki wypadek nie zmieniamy nazw klas aplikacji – łatwiejsze logi błędów.
-keepnames class pl.dom.launcher.** { *; }

# NanoHTTPD (strona konfiguracji w telefonie).
-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

# OkHttp – opcjonalne zależności.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ZXing (kod QR).
-dontwarn com.google.zxing.**

# Czytelne ślady stosu w razie awarii.
-keepattributes SourceFile,LineNumberTable
