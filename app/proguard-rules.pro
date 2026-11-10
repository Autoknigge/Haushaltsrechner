# Eigene Regeln sind nicht nötig; Daten werden mit org.json gelesen, ohne Reflection.

-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.**
-keep class com.tom_roush.pdfbox.** { *; }
