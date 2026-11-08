# Keep data models
-keepclassmembers class Akari.Add.RSA.model.** { *; }

# Keep AES cryptographic helper and keystore classes
-keep class Akari.Add.RSA.crypto.** { *; }
-keepclassmembers class Akari.Add.RSA.crypto.** { *; }
