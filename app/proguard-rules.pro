-keep class app.paper2test.** { *; }

# PDFBox (typed-PDF text): optional JPEG-2000 decoder is not shipped; the library loads fonts and resources by name.
-dontwarn com.gemalto.jp2.JP2Decoder
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
# The website inside the app calls these (P2TApp.*): keep the annotation and the methods.
-keepattributes JavascriptInterface
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
