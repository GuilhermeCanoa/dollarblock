# DollarBlock — regras R8 (release).
#
# Room, Hilt, Compose, WorkManager, DataStore, Play Billing, Play Services Wallet e coroutines
# trazem as próprias consumer rules. Componentes do manifesto (Activities, o
# AccessibilityService, receivers) são mantidos pelas regras que o AAPT gera, então
# `DollarBlockAccessibilityService::class.java.name` continua batendo com o manifesto.
# O app não usa reflexão própria (sem Gson/Moshi/Class.forName/getIdentifier).

# Stack traces legíveis no Play Console (Falhas e ANRs) junto com o mapping.txt do AAB.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
