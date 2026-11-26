# Dom – launcher na Google TV (etap 1: szkielet)

Własny ekran główny dla telewizora Sony Bravia z Google TV.

## Co działa w tym etapie

- Ekran **Start**: zegar i data, menu (Start / Do obejrzenia / Filmy / Seriale / Aplikacje), dolny rząd ulubionych aplikacji z **oficjalnymi banerami** pobranymi z telewizora.
- Zakładka **Aplikacje**: wszystkie aplikacje. OK uruchamia aplikację, a **przytrzymanie OK** dodaje ją do ekranu Start albo z niego usuwa.
- **Przycisk Home** na pilocie otwiera ten launcher (przez usługę ułatwień dostępu).
- **Przypisanie dowolnego przycisku pilota** (np. Crunchyroll) do otwarcia launchera albo wybranej aplikacji.
- **Ochrona OLED**: interfejs przesuwa się co kilka minut o kilka pikseli. Po 5 minutach bez pilota (do zmiany) zostaje tylko mały zegar, który też się przesuwa.
- **Konfiguracja z telefonu**: Ustawienia (zębatka w prawym górnym rogu) pokazują kod QR i PIN. Na telefonie ustawisz kolejność aplikacji, akcję przycisku, czas spoczynku oraz adresy i klucze Home Assistanta, Emby, TMDB i Traktu. Klucze są szyfrowane na telewizorze kluczem z Android Keystore.

Domek, dane z Home Assistanta, seriale i filmy dojdą w kolejnych etapach. Mają już przygotowane miejsca w interfejsie.

## 1. Instalacja (bez komputera i bez ADB)

Każda zmiana w repozytorium jest automatycznie budowana przez GitHub Actions i publikowana jako wydanie z plikiem `dom-launcher.apk`.

1. Na telewizorze zainstaluj z Google Play aplikację **Downloader** (AFTVnews).
2. W Downloaderze wpisz adres:
   `https://github.com/bepropl/dom-launcher/releases/latest/download/dom-launcher.apk`
3. Pobierz plik i zainstaluj. Telewizor raz poprosi o zgodę na instalowanie aplikacji z Downloadera.
4. Na telewizorze pojawi się aplikacja **Dom**.

## 2. Aktualizacje OTA

- Launcher co kilka godzin sprawdza na GitHubie, czy jest nowsza wersja. Gdy jest, na ekranie Start pojawia się napis.
- **Ustawienia → Aktualizacje → Zainstaluj** pobiera nową wersję i prosi system o potwierdzenie.
- Przy pierwszej aktualizacji system poprosi o zgodę na instalowanie aplikacji przez „Dom”. Zgadzasz się raz.

**Podpis aplikacji:** wszystkie wersje muszą być podpisane tym samym kluczem. Klucz jest przechowywany w sekretach repozytorium (`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`). Zachowaj kopię pliku z kluczem w bezpiecznym miejscu. Bez niego nie wydasz aktualizacji dla zainstalowanej wersji.

**Budowanie lokalne (opcjonalnie):** otwórz folder w Android Studio i kliknij Run ▶. Lokalna wersja jest podpisana innym kluczem niż wersje z GitHuba, więc przed powrotem do wersji z GitHuba trzeba ją odinstalować.

## 3. Przycisk Home

1. W launcherze wejdź w **Ustawienia** (zębatka) i wybierz **Otwórz ułatwienia dostępu**.
2. Włącz usługę **„Dom – przycisk Home”** i potwierdź.
3. Od teraz Home otwiera launcher Dom.

Opcjonalnie (dla zaawansowanych) możesz też ustawić Dom jako domyślny ekran główny przez ADB:

```
adb shell cmd package set-home-activity pl.dom.launcher/.MainActivity
```

**Nie wyłączaj** fabrycznego launchera Google (`com.google.android.apps.tv.launcherx`). U części osób kończyło się to pętlą restartów. Ten projekt go nie potrzebuje.

**Jak to cofnąć:** wyłącz usługę „Dom – przycisk Home” w ułatwieniach dostępu albo odinstaluj aplikację „Dom” w ustawieniach aplikacji telewizora.

## 4. Przycisk do przypisania (np. Crunchyroll)

Ustawienia → **Przypisz przycisk** → w ciągu 15 sekund wciśnij przycisk na pilocie. Domyślnie przypisany przycisk otwiera launcher. Na stronie w telefonie możesz zamiast tego wybrać dowolną aplikację. Część przycisków aplikacji Sony może obsługiwać system wcześniej niż usługa. Jeśli jakiś przycisk się nie da przypisać, to właśnie ten przypadek.

## 5. Konfiguracja z telefonu

Ustawienia → zeskanuj kod QR telefonem w tej samej sieci. PIN jest wpisywany automatycznie z kodu, a w razie potrzeby wpiszesz go ręcznie. Strona działa tylko wtedy, gdy na telewizorze jest otwarty ekran Ustawień. Po 10 błędnych PIN-ach blokuje się do ponownego otwarcia ekranu.

Pola z kluczami zostawione puste nie zmieniają zapisanej wartości. Wpisanie `-` usuwa klucz.

## Struktura projektu

```
app/src/main/java/pl/dom/launcher/
  MainActivity.kt            ekran główny, budzenie z trybu spoczynku
  LauncherApp.kt             wspólne obiekty
  data/                      ustawienia (DataStore), zaszyfrowane klucze, lista aplikacji
  service/                   usługa przycisku Home, nauka przycisków, aktywność pilota
  config/                    serwer strony dla telefonu, strona HTML, kod QR
  update/                    aktualizacje OTA z wydań na GitHubie
  ui/                        Compose for TV: pasek górny, menu, Start, Aplikacje, Ustawienia
```

