# DahaMusic — rozwój i synchronizacja

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

Baza `media_player.db` ma wersję 4. Migracja 3 → 4 zachowuje playlisty i ulubione,
dodaje dostępność utworu i unikalny indeks URI. Duplikaty URI ze starych importów
są scalane z zachowaniem najstarszego ID i flagi ulubionego z dowolnego duplikatu.
Eksporty schematów 3 i 4 znajdują się w `app/schemas/`.
Nie używamy destrukcyjnego odtwarzania bazy.
Testy konwertera sprawdzają odczyt dotychczasowego JSON-u playlist
oraz zachowanie identyfikatorów, kolejności i flag po zapisie/odczycie.

Odtwarzanie realizuje Media3 1.6.1 (`ExoPlayer` + `MediaSessionService`).
Ekran pozostaje w XML/View Binding. Podniesienie `targetSdk` pozostaje osobnym etapem.

Testy JVM i kompilacja APK nie zastępują sprawdzenia na urządzeniu: przed
wydaniem należy zweryfikować aktualizację istniejącej instalacji z zachowaniem
playlist i ulubionych, odtwarzanie, pauzę, zmianę utworu i sterowanie systemowe.
Testy urządzeniowe uruchamia się przez `bash gradlew :app:connectedDebugAndroidTest`
z podłączonym urządzeniem lub emulatorem.


## Synchronizacja biblioteki

Biblioteka odświeża się po przyznaniu dostępu do audio i po powrocie do ekranu
biblioteki. Przycisk „Odśwież” uruchamia ją ręcznie. Podczas wyświetlania tego
ekranu `ContentObserver` wykrywa zmiany w MediaStore i grupuje powiadomienia.
Źródłem jest indeks multimediów Androida: plik musi zostać najpierw rozpoznany
przez system, aby aplikacja go zobaczyła.

Odczyt odbywa się poza głównym wątkiem; kompletny wynik jest zapisywany w jednej
transakcji. Błąd odczytu lub utrata uprawnienia nie zerują biblioteki. Udany
odczyt bez danego URI oznacza niedostępność utworu: wpis pozostaje w bazie i na
własnych playlistach, ale jest pomijany przy wyświetlaniu i odtwarzaniu. Powrót
tego samego URI przywraca dostępność, ID i ulubione. Ponowne dodanie pliku z innym
URI jest traktowane jako nowy utwór.

„Your musics” i „Favorites” są listami systemowymi. Własne playlisty zachowują
kolejność i otrzymują aktualne metadane. Pozostają zapisane w dotychczasowym JSON-ie;
pełna normalizacja relacji playlist nie jest częścią tej zmiany.

Testy Robolectric sprawdzają rzeczywiste operacje Room/SQLite, migrację z eksportu
wersji 3, duplikaty, ulubione, niedostępne pliki, błędy zapisu i odczytu oraz
równoczesne odświeżenia. Testy źródła audio używają dostawcy MediaStore w środowisku
Robolectric. Przy pierwszym uruchomieniu testów potrzebny jest dostęp do Maven
Central w celu pobrania testowego Androida; nie wymaga to emulatora ani telefonu.

Sprawdzenie na telefonie (aktualizacja istniejącej instalacji, bez odinstalowania):

1. Zachowaj utwór w ulubionych i na własnej playliście.
2. Dodaj nowy plik audio, wróć do biblioteki i sprawdź, czy pojawi się raz.
3. Użyj „Odśwież” kilkukrotnie i sprawdź ulubione oraz kolejność playlisty.
4. Wyszukaj utwór na liście i sprawdź, czy kliknięcie odtwarza właściwy plik.
5. Usuń plik lub odłącz nośnik i sprawdź, czy niedostępny utwór nie jest odtwarzany.
6. Przywróć nośnik z zachowanym URI i sprawdź powrót utworu na playlistę.
7. Sprawdź dodawanie/usuwanie ulubionych po zmianie kolejności biblioteki.

## Odtwarzanie w serwisie Media3

`PlaybackService` jest właścicielem jednego ExoPlayera i jednej sesji MediaSession.
`MusicActivity` łączy się przez MediaController w `onStart` i zwalnia wyłącznie
kontroler w `onStop`. Serwis zarządza powiadomieniem multimedialnym i trybem
foreground, audio focus i pauzą po odłączeniu słuchawek. Player jest zwalniany
przy zniszczeniu serwisu, a nie przy zamknięciu ekranu.

Powiadomienie otwiera ekran bieżącej kolejki bez ponownego wybierania folderu.
Otwarcie aktualnego utworu z niezmienionej kolejki zachowuje pozycję; jawne
wybranie innego utworu ustawia jego początek. Obrót ekranu nie wznawia samoczynnie
pauzy. Stan powtarzania i losowania należy do serwisu. Domyślnie kolejka jest
zapętlona; włączenie powtarzania jednego utworu wyłącza losowanie i odwrotnie.
Przyciski poprzedni/następny używają komend Media3 także w powiadomieniu;
poprzedni może najpierw wrócić na początek bieżącego utworu.

Po usunięciu zadania z ostatnich aplikacji Media3 domyślnie utrzymuje odtwarzanie
w foreground, jeśli faktycznie trwa. Zadanie w pauzie/bez odtwarzania kończy
serwis. Kolejka nie jest jeszcze zapisywana do trwałej pamięci na wypadek zabicia
procesu, wymuszonego zatrzymania aplikacji czy restartu telefonu.

Aplikacja nie deklaruje uprawnienia INTERNET. Kolejka akceptuje lokalne URI
content/file/android.resource; obce kontrolery mogą sterować odtwarzaniem, ale
nie mogą podmieniać kolejki. Nie ma starego BroadcastReceivera, ręcznie tworzonego
powiadomienia ani drugiego serwisu czyszczącego powiadomienie. Schemat bazy pozostaje
w wersji 4, bez kolejnej migracji.

Testy Robolectric tworzą rzeczywisty serwis, ExoPlayera, MediaSession i kontrolery.
Sprawdzają kolejkę, pozycję przy ponownym połączeniu, komendy transportowe,
uprawnienia kontrolerów, filtrowanie URI i zakończenie serwisu w pauzie.
Nie dekodują plików audio i nie zastępują testu dźwięku na urządzeniu.

Sprawdzenie na telefonie po aktualizacji:

1. Włącz utwór, wyjdź strzałką do folderu i biblioteki: muzyka ma grać dalej.
2. Otwórz powiadomienie i ten sam utwór: pozycja ma zostać zachowana.
3. Włącz pauzę, obróć ekran i otwórz powiadomienie: pauza ma pozostać.
4. Sprawdź następny/poprzedni, przewijanie i zgodność tytułu w ekranie i powiadomieniu.
5. Sprawdź automatyczne przejście na koniec pliku, pętlę kolejki, repeat-one i shuffle.
6. Wygasz ekran i sprawdź sterowanie z blokady oraz metadane/okładkę.
7. Odłącz słuchawki: odtwarzanie powinno się zatrzymać. Sprawdź również reakcję
   na inny odtwarzacz i połączenie telefoniczne.
8. Usuń zadanie z ostatnich aplikacji podczas grania, a następnie podczas pauzy.
9. Sprawdź serduszko i dodanie aktualnego utworu do playlisty po zmianie utworu
   przyciskiem powiadomienia.
