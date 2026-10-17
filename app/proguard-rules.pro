# Nada e removido pelo R8 neste projeto (minify esta desligado),
# mas se um dia ligar, mantenha estas regras.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.**
-dontwarn java.awt.**
