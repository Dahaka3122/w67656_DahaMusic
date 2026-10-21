# DahaMusic

Lokalny odtwarzacz muzyki na Androida. Biblioteka pochodzi z systemowego
MediaStore; utwory, ulubione i playlisty są przechowywane lokalnie.

## Budowanie

Zestaw narzędzi dla tego etapu modernizacji:

- JDK 21 do uruchamiania Gradle (ustaw `JAVA_HOME` lub Gradle JDK w Android Studio).
- Gradle 8.11.1 z dołączonego wrappera, Android Gradle Plugin 8.10.0, Kotlin 2.2.0.
- Android SDK Platform 35 i Build Tools 35.0.0.
- `minSdk` 23 (Android 6), `targetSdk` 33. Aktualizacja targetu jest osobnym etapem.

Wskaż SDK przez `ANDROID_HOME` lub lokalny, nieśledzony `local.properties`
z wpisem `sdk.dir=/sciezka/do/android-sdk`. Nie zapisuj ścieżek lokalnych w Git.

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
bash gradlew :app:assembleDebug :app:testDebugUnitTest
bash gradlew :app:assembleDebugAndroidTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Raporty: `app/build/reports/tests/testDebugUnitTest/` i
`app/build/reports/lint-results-debug.html`.

Kompilacja kodu nadal używa docelowego bytecode Java/Kotlin 1.8. Wersja JDK
uruchamiającego Gradle nie podnosi minimalnej wersji Androida.

## Dane i zakres modernizacji

Baza `media_player.db` zachowuje wersję 3. Eksport schematu znajduje się
w `app/schemas/`; zmiana struktury bazy w przyszłości wymaga jawnej migracji.
Nie używamy destrukcyjnego odtwarzania bazy.
Testy konwertera sprawdzają odczyt dotychczasowego JSON-u playlist
oraz zachowanie identyfikatorów, kolejności i flag po zapisie/odczycie.

Odtwarzanie nadal realizuje `MediaPlayer`, a ekran pozostaje w XML/View Binding.
Synchronizacja biblioteki, przebudowa odtwarzania na serwis/Media3 i podniesienie
`targetSdk` to oddzielne etapy.

Testy JVM i kompilacja APK nie zastępują sprawdzenia na urządzeniu: przed
wydaniem należy zweryfikować aktualizację istniejącej instalacji z zachowaniem
playlist i ulubionych, odtwarzanie, pauzę, zmianę utworu i sterowanie systemowe.
Testy urządzeniowe uruchamia się przez `bash gradlew :app:connectedDebugAndroidTest`
z podłączonym urządzeniem lub emulatorem.

Lint ma lokalnie udokumentowany wyjątek w `CreateNotification`: powiadomienia
`MediaStyle` z tokenem `MediaSession` są zwolnione z wymogu `POST_NOTIFICATIONS`.
Wyjątek nie obejmuje ewentualnych przyszłych powiadomień innego rodzaju.
