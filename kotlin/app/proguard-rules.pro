# PixelCat ProGuard Rules
# WebView JS interface
-keepclassmembers class com.pixelcat.overlay.** {
    @android.webkit.JavascriptInterface <methods>;
}
# Keep overlay service & supabase client
-keep class com.pixelcat.overlay.PetOverlayService { *; }
-keep class com.pixelcat.overlay.PetOverlayService$PetHost { *; }
-keep class com.pixelcat.overlay.SupabaseClient { *; }
-keep class com.pixelcat.overlay.SupabaseClient$PetEvent { *; }
# OkHttp & Gson
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn okhttp3.**
-dontwarn com.google.gson.**
