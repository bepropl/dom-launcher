# Dom – launcher na Google TV

Własny ekran główny dla telewizora Sony Bravia z Google TV.

## Co działa

- Ekran **Start**: zegar i data, menu (Start / Do obejrzenia / Filmy / Seriale / Aplikacje), dolny rząd ulubionych aplikacji z **oficjalnymi banerami** pobranymi z telewizora.
- Zakładka **Aplikacje**: wszystkie aplikacje. OK uruchamia aplikację, a **przytrzymanie OK** dodaje ją do ekranu Start albo z niego usuwa.
- Ekran **Start**: **przytrzymanie OK** na aplikacji włącza przesuwanie – ◀ ▶ zmienia jej miejsce, OK albo Wstecz zapisuje kolejność.
- **Dom jako ekran główny**: usługa ułatwień dostępu przejmuje przycisk Home i ekran po włączeniu telewizora (szczegóły w punkcie 3).
- **Ochrona OLED**: interfejs przesuwa się co kilka minut o kilka pikseli. Po 5 minutach bez pilota (do zmiany) zostaje tylko mały zegar, który też się przesuwa.
- **Konfiguracja z telefonu**: Ustawienia (zębatka w prawym górnym rogu) pokazują kod QR i PIN. Na telefonie ustawisz kolejność aplikacji, czas spoczynku oraz adresy i klucze Home Assistanta, Emby i TMDB, serwisy streamingowe oraz obserwowane seriale. Klucze są szyfrowane na telewizorze kluczem z Android Keystore.

- **Domek w tle**: chmura punktów Waszego domu (OpenGL), powoli się obraca. W Ustawieniach: Pełna / Oszczędna / Wyłączony. Gdy launcher jest w tle, nic się nie rysuje.
- **Seriale i filmy** (TMDB + własna lista w launcherze, bez Traktu):
  - Start: nowe odcinki obserwowanych seriali i „Kontynuuj” z Emby.
  - Do obejrzenia: „Dalej”, lista do obejrzenia, seriale czekające na nowe odcinki.
  - Filmy i Seriale: polecane w Waszych serwisach (TMDB, region PL) i nowości z Emby na NAS.
  - OK otwiera aplikację serwisu, **przytrzymanie OK** pokazuje menu (oznacz odcinek jako obejrzany, obserwuj, dodaj do listy…).

- **Home Assistant** (stałe połączenie WebSocket):
  - pasek u góry: pogoda, temperatura na zewnątrz i w domu, kto jest w domu,
  - światła na domku: włączona lampa rozjaśnia swój pokój,
  - powiadomienia: zdarzenie `dom_launcher_notify` z automatyzacji (tytuł, treść, opcjonalnie obraz z kamery). W launcherze dymek pokazuje sam launcher, nad innymi aplikacjami – usługa „Dom – ekran główny”. Dzwonek w pasku pokazuje ostatnie powiadomienia.
  - HA z własnym certyfikatem: na stronie w telefonie pojawi się odcisk certyfikatu i przycisk „Ufaj”.

Domofon (podgląd i otwieranie furtki) dojdzie, gdy będzie w HA.

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

## 3. Przycisk Home i przyciski pilota

Dom ma usługę ułatwień dostępu **„Dom – ekran główny”** (działa jak „Override” w Projectivy):
- przycisk Home otwiera Dom,
- gdy po włączeniu telewizora pojawi się ekran Google TV, Dom od razu go przykrywa.

Usługa nie czyta zawartości ekranu – dostaje tylko informację, że na wierzch wyszedł launcher Google.

1. Google Play Protect blokuje instalację spoza Sklepu aplikacji z taką usługą. Wyłącz skanowanie: Sklep Play → profil → Play Protect → zębatka → „Skanuj aplikacje za pomocą Play Protect”.
2. Zainstaluj Dom, potem Ustawienia Dom → „Otwórz Ułatwienia dostępu” → „Dom – ekran główny” → Włącz.
3. Jeśli wcześniej Home był przypisany w Button Mapperze, usuń to przypisanie (inne przyciski, np. Crunchyroll, możesz tam zostawić).

**Nie wyłączaj** fabrycznego launchera Google (`com.google.android.apps.tv.launcherx`). U części osób kończyło się to pętlą restartów.

## 4. Konfiguracja z telefonu

Ustawienia → zeskanuj kod QR telefonem w tej samej sieci. PIN jest wpisywany automatycznie z kodu, a w razie potrzeby wpiszesz go ręcznie. Strona działa tylko wtedy, gdy na telewizorze jest otwarty ekran Ustawień. Po 10 błędnych PIN-ach blokuje się do ponownego otwarcia ekranu.

Pola z kluczami zostawione puste nie zmieniają zapisanej wartości. Wpisanie `-` usuwa klucz.

Na tej samej stronie:
- zaznaczasz swoje serwisy streamingowe (z nich są polecane filmy i seriale),
- wyszukujesz seriale i filmy: **Obserwuj** (na bieżąco albo od początku) i **Do obejrzenia**,
- poprawiasz „obejrzane do SxxEyy” przy obserwowanych serialach,
- pobierasz i wczytujesz kopię zapasową listy (plik JSON).

Klucz TMDB jest darmowy: themoviedb.org → Ustawienia → API (działa klucz v3 albo token odczytu v4).

## Struktura projektu

```
app/src/main/java/pl/dom/launcher/
  MainActivity.kt            ekran główny, budzenie z trybu spoczynku
  LauncherApp.kt             wspólne obiekty
  data/                      ustawienia (DataStore), zaszyfrowane klucze, lista aplikacji
  house/                     domek: model punktów (assets/house.bin) i renderer OpenGL ES 2.0
  media/                     TMDB, Emby, własna lista seriali i filmów
  service/                   aktywność pilota (tryb spoczynku)
  config/                    serwer strony dla telefonu, strona HTML, kod QR
  update/                    aktualizacje OTA z wydań na GitHubie
  ui/                        Compose for TV: pasek górny, menu, zakładki, karty, Ustawienia
tools/house-model.js         generator assets/house.bin (node tools/house-model.js)
```

