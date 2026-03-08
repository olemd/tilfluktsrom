# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
