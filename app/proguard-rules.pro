# Keep data models
-keep class Akari.Add.RSA.model.** { *; }
-keepclassmembers class Akari.Add.RSA.model.** { *; }

# Keep managers
-keep class Akari.Add.RSA.manager.** { *; }
-keepclassmembers class Akari.Add.RSA.manager.** { *; }

# Keep AES cryptographic helper and keystore classes
-keep class Akari.Add.RSA.crypto.** { *; }
-keepclassmembers class Akari.Add.RSA.crypto.** { *; }
