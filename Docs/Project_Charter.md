# Project Charter (Karta Projektu)

## 1. Kontekst i cel projektu
* **Tło problemu:** Codzienne funkcjonowanie studentów w domach studenckich politechniki opiera się wciąż na przestarzałych, analogowych metodach. Rezerwacja pralni oraz zgłaszanie awarii w pokojach wymagają fizycznej wizyty na recepcji/portierni i ręcznego wpisywania się do papierowych zeszytów, co prowadzi do błędów, braku transparentności i konfliktów o dostępność sprzętu. Brakuje scentralizowanego kanału do sprawdzania dostępności salek tematycznych (nauki, TV, rekreacji), a kluczowe komunikaty administracji (wyłączenia internetu, terminy wymiany pościeli) giną na kartkach przyklejanych na drzwiach. Dodatkowo weryfikacja tożsamości mieszkańca na portierni opiera się na nietrwałych, papierowych laminatach, a studenci nie mają wygodnego, dedykowanego kanału do szybkiej integracji i pomocy sąsiedzkiej (np. pożyczenie soli, wymiana sprzętu).
* **Cel projektu (SMART):** Zaprojektowanie, zaimplementowanie oraz przetestowanie funkcjonalnego MVP aplikacji webowej PKampus o architekturze Progressive Web App (PWA) i responsywnym interfejsie (RWD). System zapewni dwutorową obsługę: pełną cyfryzację procesów administracyjno-bytowych (rezerwacje pralni i salek, zgłaszanie usterek ze zdjęciami, wirtualna karta mieszkańca z elementem anty-screenshot) oraz moduł społecznościowo-informacyjny (tablica ogłoszeń, wątki pomocy sąsiedzkiej, oficjalne komunikaty i kalendarz wydarzeń) w architekturze zdolnej do równoległej obsługi co najmniej 2 akademików, zrealizowane do terminu złożenia pracy inżynierskiej.
* **Wartość:** 
  * *Dla mieszkańców:* Oszczędność czasu dzięki rezerwacjom 24/7 z telefonu, bieżący podgląd stanu zgłoszonych usterek, szybki dostęp do cyfrowej legitymacji mieszkańca, transparentne informacje o życiu kampusu oraz ułatwiona integracja sąsiedzka.
  * *Dla personelu (portiernia, konserwatorzy, administracja):* Całkowita eliminacja papierowych zeszytów, redukcja kolejek i rutynowych pytań, uporządkowany rejestr usterek ze statusem naprawy oraz bezproblemowa publikacja ogłoszeń technicznych.

## 2. Interesariusze i role
* **Sponsor / Klient / Promotor:** Promotor pracy inżynierskiej (ocena merytoryczna i inżynierska), Kierownictwo Osiedla Studenckiego / Dział Domów Studenckich Politechniki (potencjalny beneficjent rozwiązania).
* **Zespół projektowy:** Autor pracy inżynierskiej (rola: Fullstack Developer / Inżynier Oprogramowania – odpowiedzialny za analizę wymagań, architekturę, implementację frontendu i backendu, testy oraz dokumentację).
* **Docelowi odbiorcy:** Studenci Politechniki zamieszkujący domy studenckie, pracownicy recepcji/portierni oraz kierownictwo akademików.

## 3. Aktorzy systemu
* **Mieszkaniec (Student):** Użytkownik zalogowany, przypisany do konkretnego akademika i numeru pokoju. Posiada dostęp do: wirtualnej karty mieszkańca, rezerwacji pralni i salek tematycznych, zgłaszania usterek w swoim pokoju/częściach wspólnych, tablicy ogłoszeń i kalendarza oraz sekcji sąsiedzkiej.
* **Recepcjonista / Portier:** Pracownik dyżurujący na recepcji. Posiada uprawnienia do: weryfikacji tożsamości mieszkańca (podgląd wirtualnej karty), podglądu bieżących rezerwacji i wydawania kluczy (do pralni, salek), obsługi centralnego rejestru usterek (przekazywanie listy konserwatorowi bez konieczności jego logowania do systemu oraz zmiana statusów napraw zgodnie z cyklem domenowym: *Nowe* → *Przekazane konserwatorowi* → *W trakcie naprawy* → *Naprawione* / *Odrzucone* / *Wymaga części*).
* **Administrator Domu Studenckiego (ADS / Kierownik DS):** Rola systemowa `DORM_ADMIN` reprezentująca Kierownika DS oraz pracowników Administracji Domu Studenckiego (ADS w rozumieniu regulaminu to jednostka organizacyjna podległa Kierownikowi DS). W MVP MUST: weryfikuje meldunki (`FR-AUTH-02`), konfiguruje **salki** i ich parametry (`FR-ROOM-01` / `FR-PORTAL-03`); **pralki** w MVP pochodzą ze seedu (`FR-LAUND-01` SHOULD UI). SHOULD: blokady kont, sankcje, personel portierni. Publikuje oficjalne ogłoszenia/wydarzenia (SHOULD/COULD wg MoSCoW).
* **Administrator Główny / Superadmin (AOS / Kierownik OS):** Rola systemowa `SUPER_ADMIN` reprezentująca Kierownika Osiedla Studenckiego oraz pion Administracji Osiedla Studenckiego (centralna jednostka osiedla). Zarządza instancją systemu na poziomie całego miasteczka akademickiego (dodawanie/edycja obiektów akademików, globalne zarządzanie kontami administracyjnymi, publikacja oficjalnych komunikatów ogólnokampusowych w kalendarzu oraz globalna moderacja tablicy ogłoszeń).
* **System (Harmonogram zadań w tle / Spring Scheduler):** Wewnętrzny, autonomiczny proces systemowy uruchamiany cyklicznie w tle, realizujący automatyczne reguły czasowe (w tym regułę 15 minut zwalniającą nieodebrane sloty pralni i salek oraz unieważnianie przedawnionych tokenów).

## 4. Zakres projektu
* **Co wchodzi w zakres (MVP / In-Scope):**
  * **Moduł Uwierzytelniania i Profili:** Rejestracja/logowanie z podziałem na role (Mieszkaniec, Recepcjonista, Admin Akademika, Superadmin); przypisanie studenta do akademika i pokoju.
  * **Wirtualna Karta Mieszkańca (styl mObywatel):** Widok identyfikacyjny z danymi mieszkańca (zdjęcie, imię, nazwisko, akademik, numer pokoju, status ważności) oraz mechanizmami anty-fraud on-line: zegar serwera, hologram CSS, test dotykowy i kod/kolor dnia (`FR-CARD-02`) do weryfikacji na portierni w 2–3 s bez papierowego laminatu.
  * **Moduł Rezerwacji Pralni (całkowicie bezpłatny):** Wybór akademika i konkretnej pralki; harmonogram w postaci slotów czasowych zapobiegający nakładaniu się rezerwacji; reguły limitujące (max 2 aktywne = `CONFIRMED`/`KEY_ISSUED` na mieszkańca w tygodniu kalendarzowym, rezerwacja do 7 dni w przód — `BR-01`); podgląd dla recepcji w celu wydania kluczy. Lista pralek w MVP ze seedu bazy (`FR-LAUND-01` SHOULD).
  * **Moduł Rezerwacji Salek Tematycznych (całkowicie bezpłatny):** Katalog regulaminowych salek w danym akademiku (salka do cichej nauki „Kujon”, bilard, tenis stołowy, salka TV, Funzone, Audio-Video, salka Chillout — `FR-ROOM-02`); podgląd dostępności w kalendarzu i rezerwacja slotów (w fazie MVP ograniczona do mieszkańców zameldowanych w danym DS ze względów logistyki weryfikacji i wydawania kluczy na portierni).
  * **Moduł Zgłaszania Usterek (Cyfrowy Zeszyt Awarii):** Zgłaszanie problemu przez studenta (kategoria, lokalizacja/pokój, opis, załączenie zdjęcia) oraz personel (części wspólne, automatyczne auto-zgłoszenie przy wyłączeniu pralki/salki z eksploatacji); panel recepcji z rejestrem spraw i zmianą statusów (*Nowe* → *Przekazane konserwatorowi* → *W trakcie* → *Naprawione* itd.); lista własnych zgłoszeń mieszkańca z bieżącym statusem i ostatnią notatką portiera (bez pełnego dziennika w MVP). Wydruk listy dla konserwatora to rozszerzenie COULD (`FR-ISSUE-06`).
  * **Moduł Tablicy Ogłoszeń i Pomocy Sąsiedzkiej (Etap 4 / SHOULD):** Kanały ogłoszeniowe (ogólnokampusowy oraz per akademik); kategorie postów sąsiedzkich; oznaczanie postów jako rozwiązane/usuwanie przez autora — po domknięciu modułów bytowych. Komentarze jednopoziomowe to rozszerzenie COULD (`FR-BOARD-03`).
  * **Moduł Oficjalnych Komunikatów i Kalendarza:** Publikowanie ważnych ogłoszeń przez administrację (np. brak wody/internetu, terminy wymiany pościeli); kalendarz wydarzeń i imprez okolicznościowych na kampusie.
* **Co jest poza zakresem (Out of Scope):**
  * Obsługa płatności elektronicznych (rezerwacja pralek i salek jest w 100% darmowa dla mieszkańców; brak integracji z PayU/Stripe/BLIK).
  * Rezerwacje i obsługa Klubu Studenckiego „Piwnica” (obiekt ten wymaga opłat 50/100 zł, manualnego trybu rezerwacji mailowej u Koordynatora Klubu oraz dedykowanego regulaminu wykraczającego poza model bezpłatnych salek tematycznych PKampus).
  * Rezerwacje obiektów o odrębnych regulaminach (siłownie akademickie oraz sale sportowe posiadają odrębne regulaminy obiektowe, zasady BHP i procedury wstępu — są całkowicie wyłączone z systemu PKampus).
  * Integracja fizyczna IoT / Hardware (brak bezpośredniego sterowania zasilaniem pralek, brak elektrozamków i czytników RFID – klucze wydaje fizycznie portier po weryfikacji rezerwacji w aplikacji).
  * Prywatny komunikator 1-na-1 w czasie rzeczywistym (komunikacja sąsiedzka odbywa się w komentarzach pod ogłoszeniami, co zapobiega spamowi i ogranicza złożoność).
  * Bezpośrednia integracja z centralnymi systemami dziekanatowymi uczelni (np. USOS API) – dane studentów i pokoi zarządzane są wewnętrznie w systemie PKampus.
  * Dedykowane natywne aplikacje mobilne (iOS / Android w sklepach App Store / Google Play) – system jest realizowany jako responsywna aplikacja webowa (RWD / PWA) działająca na smartfonach i komputerach.
  * Powiadomienia SMS/push oraz wielopoziomowe wątki komentarzy (komentarze jednopoziomowe pod postami; notatki usterek jako pojedyncze pole `staff_notes` bez historii — `ADR-09`).

## 5. Integracje i otoczenie systemowe
* **Lokalny Object Storage (MinIO S3):** Kontener MinIO w ramach Docker Compose do przechowywania plików multimedialnych (zdjęcia do wirtualnej karty mieszkańca oraz dokumentacja fotograficzna zgłaszanych usterek).
* **Serwis poczty elektronicznej (SMTP / Mailpit):** Lokalny/darmowy serwer SMTP do powiadomień systemowych (potwierdzenia konta, reset hasła, powiadomienia o zmianie statusu usterki).
* **Granice systemu:** System projektowany w architekturze dwuprofilowej: (1) **profil demonstracyjny (lokalny / obrona pracy)** — w pełni autonomiczny i samowystarczalny pakiet kontenerowy uruchamiany lokalnie na stacji roboczej (localhost, certyfikaty lokalne/self-signed), bez twardych zależności od zewnętrznych API czy sieci kampusowej Politechniki, co gwarantuje 100% niezawodności podczas demonstracji i obrony pracy inżynierskiej; (2) **profil produkcyjny (środowisko docelowe)** — wdrożenie na serwerze uczelnianym VPS PK pod domeną uczelni, ze zautomatyzowaną terminacją TLS 1.3 przez Let's Encrypt (kontener certbot).

## 6. Wymagania pozafunkcjonalne i standardy jakościowe
* **Wydajność i współbieżność:** Czas odpowiedzi API < 200 ms dla operacji odczytu; obsługa transakcyjności i blokad (optymistycznych/pesymistycznych) na poziomie PostgreSQL/JPA przy rezerwacjach slotów pralek i salek w celu wyeliminowania podwójnych rezerwacji (race conditions / overbooking).
* **Bezpieczeństwo i prywatność:** 
  * Bezpieczna autoryzacja bezstanowa w oparciu o JWT (JSON Web Token) i Spring Security.
  * Haszowanie haseł algorytmem BCrypt.
  * Role-Based Access Control (RBAC) ściśle izolujące uprawnienia ról (Mieszkaniec, Recepcjonista, Admin Akademika, Superadmin).
  * Bezpieczny upload plików (walidacja typów MIME, limit rozmiaru zdjęć) oraz ochrona przed XSS i SQL Injection. Token JWT przekazywany w nagłówku `Authorization: Bearer` (nie w cookie) — klasyczny CSRF wobec cookie-sesji nie dotyczy tej architektury.
* **Dostępność i responsywność:**
  * Architektura Mobile-First dla widoków mieszkańca (pełna wygoda obsługi ze smartfona).
  * Dostosowanie widoków recepcji i panelu administracyjnego pod ekrany desktopowe i tablety.
  * Podstawowa dostępność zgodna z wybranymi kryteriami WCAG 2.1 AA (kontrast, fokus klawiaturowy, etykiety formularzy) — szczegóły w NFR-A11Y-01.
* **Niezawodność i konteneryzacja:** Całość usług środowiska (brama Nginx serwująca frontend React PWA, backend Spring Boot, relacyjna baza danych PostgreSQL, magazyn obiektowy MinIO S3 oraz serwer pocztowy Mailpit) uruchamiana i orkiestrowana przy użyciu pojedynczego polecenia `docker compose up -d` (zgodnie z NFR-DEP-01).

## 7. Założenia, ograniczenia i technologie
* **Stos technologiczny:**
  * **Frontend:** React / PWA (nowoczesny UI, komponenty funkcyjne, stylizacja Tailwind CSS, responsywność RWD, instalowalność PWA).
  * **Brama wejściowa serwera (Reverse Proxy):** Nginx (terminacja TLS 1.3, nagłówki bezpieczeństwa CSP/HSTS wg NFR-SEC-04, serwowanie skompilowanych zasobów PWA oraz proxy dla ścieżek `/api/*`).
  * **Backend:** Java 21 (LTS) + Spring Boot (Spring Web, Spring Security, Spring Data JPA, Hibernate, Maven).
  * **Baza danych:** PostgreSQL 16 (relacyjny model danych, integralność referencyjna, transakcje ACID, ograniczenia `EXCLUDE USING gist`).
  * **Magazyn obiektowy:** MinIO S3 (kompatybilny z AWS S3 API, prywatne buckety zdjęć kart i usterek).
  * **Serwer pocztowy:** Mailpit (lokalny serwer SMTP do powiadomień e-mail i testowania bez ryzyka spamu).
  * **Konteneryzacja i orkiestracja:** Docker & Docker Compose (sieć mostkowa `pkampus-net`, wolumeny trwałe `pg_data`, `minio_data`, `letsencrypt_certs`, `certbot_challenge` — por. `Diagram_Wdrozenia.md`).
* **Ograniczenia zasobowe i budżetowe:**
  * Projekt realizowany jednoosobowo w ramach pracy inżynierskiej w horyzoncie czasowym jednego semestru akademickiego.
  * Budżet finansowy: 0 PLN (wykorzystanie wyłącznie technologii Open Source, narzędzi darmowych, darmowych certyfikatów Let's Encrypt oraz lokalnego/uczelnianego środowiska wdrożeniowego).
* **Kluczowe założenia wstępne:**
  * Użytkownicy dysponują urządzeniem z aktualną przeglądarką internetową (smartfon/PC).
  * Pokój w akademiku oraz dane studenta są wstępnie weryfikowane przez administrację domu studenckiego.

## 8. Kryteria sukcesu i odbioru (KPI & Acceptance Criteria)
* **Wskaźniki sukcesu (KPI / OKR):**
  * Zastąpienie 100% papierowych zeszytów rezerwacji pralni i rejestru usterek rozwiązaniem cyfrowym w scenariuszach testowych.
  * Czas zgłoszenia usterki oraz czas rezerwacji slotu pralki przez mieszkańca skrócony do poniżej 30 sekund z poziomu smartfona.
  * Brak jakichkolwiek podwójnych rezerwacji na ten sam slot czasowy dla danego zasobu (pralka / salka).
* **Kryteria odbioru (Definition of Done pracy inżynierskiej):**
  * Bezbłędne, w pełni zautomatyzowane uruchomienie całego stosu (brama Nginx z PWA + Spring Boot + PostgreSQL + MinIO + Mailpit) za pomocą pojedynczego polecenia `docker compose up -d` (zgodnie z NFR-DEP-01).
  * Zestaw testów jednostkowych i integracyjnych backendu (JUnit 5, Mockito, Testcontainers lub baza testowa H2/PostgreSQL) weryfikujących poprawność reguł biznesowych (np. walidacja limitów rezerwacji, autoryzacja ról).
  * Przygotowanie bazy danych z przykładowymi danymi początkowymi (seed data dla 2 akademików, pokoi, użytkowników testowych i salek).
  * Kompletna dokumentacja techniczna, architektoniczna i użytkowa zawarta w pisemnej części pracy inżynierskiej oraz bezproblemowa demonstracja scenariusza działania przed komisją egzaminacyjną.

## 9. Wstępna analiza ryzyk
* **Ryzyko współbieżności rezerwacji (Race Condition):** Jednoczesna próba rezerwacji tej samej pralki w tej samej sekundzie przez dwóch mieszkańców.
  * *Mitygacja:* Ograniczenie PostgreSQL `EXCLUDE USING gist` na przedziale `tstzrange(start_time, end_time)` (per zasób) oraz transakcje Spring Data JPA; kolizja zwraca SQLSTATE `23P01` (`exclusion_violation`).
* **Ryzyko fałszowania wirtualnej karty mieszkańca:** Używanie zrzutów ekranu (screenshotów) przez osoby niebędące mieszkańcami akademika do wejścia na obiekt.
  * *Mitygacja:* Karta on-line (`FR-CARD-02`): zegar serwera (sekundnik), animowany hologram CSS, interaktywny test dotykowy (ripple) przeciw nagraniom oraz dobowy kod/kolor weryfikacyjny zgodny z ekranem portierni; offline/screenshot nieważny.
* **Ryzyko blokowania zasobów (zjawisko „widmo-rezerwacji”):** Mieszkańcy rezerwują wiele slotów pralek i nie zjawiają się na pranie.
  * *Mitygacja:* Limit max 2 aktywne rezerwacje pralni (`CONFIRMED`/`KEY_ISSUED`) na mieszkańca w tygodniu kalendarzowym (`BR-01`) oraz reguła 15 min: automatyczne zwolnienie przez scheduler co 1 min (`NFR-REL-03`) lub ręcznie przez portiera (`BR-02`, `AUTO_CANCELLED_15MIN`).
* **Ryzyko niestosownych treści na tablicy sąsiedzkiej:** Publikacja spamu lub treści wulgarnych.
  * *Mitygacja:* Całkowity brak anonimowości – każdy wpis i komentarz jest podpisany imieniem i nazwiskiem autora (w zasięgu akademika `DORMITORY`: dodatkowo numer pokoju i DS; w zasięgu kampusowym `CAMPUS`: wyłącznie nazwa akademika bez numeru pokoju wg `FR-BOARD-05`); administrator akademika posiada uprawnienia do usuwania postów.
* **Ryzyko czasowe i zakresowe (1-osobowy zespół projektowy):** Złożoność pełnego stosu fullstack w semestrze dyplomowym.
  * *Mitygacja:* Ścisłe podejście MVP (najpierw backend i kluczowe moduły bytowe, w dalszej kolejności tablica sąsiedzka); rezygnacja z IoT, płatności i prywatnych czatów 1-na-1.

## 10. Plan ramowy i kamienie milowe
* **Etap 1: Wymagania i Modelowanie:** Szczegółowa specyfikacja wymagań funkcjonalnych i pozafunkcjonalnych, diagramy przypadków użycia (UML Use Case), projekt architektury i diagram encji bazodanowych (ERD).
* **Etap 2: Środowisko i Autoryzacja:** Konfiguracja repozytorium, Docker Compose (PostgreSQL, MinIO), szkielet Spring Boot i React, wdrożenie JWT i systemu ról (RBAC).
* **Etap 3: Moduły Bytowo-Administracyjne:** Implementacja silnika rezerwacji pralni i salek, modułu zgłaszania i obsługi usterek, wirtualnej karty mieszkańca oraz panelu recepcji.
* **Etap 4: Moduły Społecznościowo-Informacyjne:** Implementacja tablicy ogłoszeń sąsiedzkich z filtrowaniem i komentarzami oraz kalendarza oficjalnych komunikatów i wydarzeń.
* **Etap 5: Testy, Refaktoryzacja i Dane Testowe:** Testy jednostkowe i integracyjne backendu, weryfikacja RWD, przygotowanie skryptów zasilających bazę danymi demonstracyjnymi.
* **Etap 6: Finałowa Dokumentacja i Obrona:** Opracowanie tekstu pracy inżynierskiej, przygotowanie prezentacji i scenariusza demonstracyjnego.

## 11. Słownik pojęć (Glosariusz)
* **Mieszkaniec:** Student Politechniki zameldowany w określonym domu studenckim i pokoju, posiadający aktywne konto w systemie.
* **Portiernia / Recepcja:** Punkt obsługi stacjonarnej w akademiku, odpowiedzialny za weryfikację tożsamości, wydawanie fizycznych kluczy oraz przekazywanie usterek konserwatorowi.
* **Wirtualna Karta Mieszkańca:** Cyfrowy dokument tożsamości w aplikacji z weryfikacją wizualną, zastępujący papierową, laminowaną kartę mieszkańca.
* **Slot rezerwacyjny:** Dyskretny przedział czasu (np. 1.5 lub 2 godziny), na który można zarezerwować konkretne urządzenie (pralkę) lub salkę.
* **Salka tematyczna:** Pomieszczenie wspólne w akademiku objęte Regulaminem korzystania z salek tematycznych PK (np. salka cichej nauki „Kujon”, salka TV, bilard, tenis stołowy, Funzone, Chillout). Obiekty sportowe (siłownie) podlegają osobnym regulaminom i są wyłączone z systemu.
* **Cyfrowy Zeszyt Awarii:** Moduł ewidencji zgłoszeń usterek zastępujący fizyczny zeszyt papierowy na portierni.
* **Pomoc Sąsiedzka:** Kategoria na tablicy ogłoszeń dedykowana szybkiej wymianie dóbr i wzajemnej pomocy pomiędzy mieszkańcami (np. pożyczenie drobnych przedmiotów, sprzętu).
* **ADS (Administracja Domu Studenckiego):** Jednostka organizacyjna zarządzająca danym domem studenckim pod kierownictwem Kierownika DS; w systemie PKampus tożsama z rolą administratora danego obiektu (`DORM_ADMIN`).
* **AOS (Administracja Osiedla Studenckiego):** Centralna jednostka organizacyjna osiedla pod kierownictwem Kierownika OS; w systemie PKampus tożsama z rolą administratora globalnego / Superadmina (`SUPER_ADMIN`).
* **KOS (Komisja Osiedla Studenckiego):** Organ Samorządu Studenckiego Politechniki Krakowskiej reprezentujący mieszkańców; organ wnioskujący i opiniujący w sprawach studenckich (np. wnioskowanie o sankcje dyscyplinarne), którego dyspozycje operacyjne wprowadza do systemu administrator ADS.
