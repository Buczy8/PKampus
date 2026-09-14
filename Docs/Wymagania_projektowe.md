# Analiza Wymagań Projektowych - System PKampus

Dokument specyfikacji wymagań funkcjonalnych, pozafunkcjonalnych i reguł biznesowych dla systemu PKampus (praca inżynierska).

---

## 1. Wstęp i Konwencja Oznaczeń
* **FR (Functional Requirement):** Wymaganie funkcjonalne
* **NFR (Non-Functional Requirement):** Wymaganie pozafunkcjonalne
* **BR (Business Rule):** Reguła biznesowa
* **Priorytetyzacja MoSCoW:**
  * **MUST:** Bezwzględnie wymagane w MVP
  * **SHOULD:** Ważne, powinno się znaleźć w projekcie
  * **COULD:** Opcjonalne, mile widziane
  * **WON'T:** Poza zakresem MVP (odłożone)

---

## 2. Moduł Uwierzytelniania i Zarządzania Kontami (AUTH)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-AUTH-01** | Rejestracja mieszkańca i weryfikacja e-mail | MUST | Użytkownik rejestruje się podając: imię, nazwisko, adres e-mail, hasło, numer telefonu, wybiera akademik z listy, podaje numer pokoju oraz załącza zdjęcie twarzy (do karty mieszkańca). Deklarowany numer pokoju oraz identyfikator akademika zapisywane są w profilu użytkownika (`users.declared_room_number`, `users.dormitory_id`). System wysyła link aktywacyjny w celu potwierdzenia adresu e-mail (`PENDING_EMAIL`). Link zawiera **krótkotrwały, podpisany token** (np. JWT/HMAC, TTL ok. 24 h) weryfikowany kryptograficznie po stronie API — **bez osobnej tabeli tokenów w bazie** (w odróżnieniu od resetu hasła). |
| **FR-AUTH-02** | Weryfikacja meldunku (Aktywacja przez Administratora DS) | MUST | Po potwierdzeniu adresu e-mail konto otrzymuje status `PENDING_APPROVAL`. Administrator Domu Studenckiego (ADS) weryfikuje dane studenta i deklarowany numer pokoju (`declared_room_number`) z uczelnianą listą meldunkową i aktywuje konto (status `ACTIVE`), co tworzy formalny rekord kwaterunku w `room_assignments`. Do momentu aktywacji student widzi ekran informacyjny (Onboarding lock) bez dostępu do rezerwacji i karty. |
| **FR-AUTH-03** | Uwierzytelnianie JWT | MUST | Logowanie za pomocą adresu e-mail i hasła. Po poprawnym uwierzytelnieniu system zwraca token JWT zawierający identyfikator użytkownika, przypisany akademik, numer pokoju oraz role (`RESIDENT`, `RECEPTIONIST`, `DORM_ADMIN`, `SUPER_ADMIN`). |
| **FR-AUTH-04** | Kontrola dostępu oparta na rolach (RBAC) | MUST | Dostęp do poszczególnych endpointów API oraz widoków frontendowych jest ściśle ograniczony do posiadanych ról. |
| **FR-AUTH-05** | Zarządzanie personelem portierni | MUST | Administrator Akademika ma możliwość tworzenia, edycji i dezaktywacji kont z rolą `RECEPTIONIST` dla swojego akademika. |
| **FR-AUTH-06** | Edycja profilu i zmiana hasła | SHOULD | Użytkownik ma możliwość zmiany hasła oraz aktualizacji danych kontaktowych (np. numeru telefonu). Zmiana numeru pokoju lub akademika wymaga zatwierdzenia przez Administratora Domu Studenckiego (ADS). |
| **FR-AUTH-07** | Procedura resetowania hasła (e-mail token) | MUST | Użytkownik ma możliwość zresetowania zapomnianego hasła poprzez podanie adresu e-mail. System generuje jednorazowy token kryptograficzny (TTL: 15 minut) przesyłany linkiem e-mail (Mailpit/SMTP). Użycie linku otwiera formularz zdefiniowania nowego hasła, po czym token ulega natychmiastowemu unieważnieniu. |

---

## 3. Moduł Wirtualnej Karty Mieszkańca (CARD)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :--- | :--- |
| **FR-CARD-01** | Wyświetlanie karty mieszkańca | MUST | Dedykowany, pełnoekranowy widok w aplikacji (wzorowany na dokumencie tożsamości mObywatel), prezentujący: zdjęcie mieszkańca, imię i nazwisko, nazwę akademika, numer pokoju, rok akademicki oraz oficjalną pieczęć graficzną. |
| **FR-CARD-02** | Mechanizm anty-screenshot (Weryfikacja dynamiczna) | MUST | Karta zawiera działający na żywo zegar (godzina, minuta, sekundy) oraz animowany element pulsujący/ruchomy (hologram wizualny), uniemożliwiający posługiwanie się statycznymi zrzutami ekranu przez osoby nieuprawnione. |
| **FR-CARD-03** | Dynamiczny wskaźnik statusu ważności | MUST | Karta wyraźnie sygnalizuje status konta na podstawie enumu `users.status`: zielony „AKTYWNA / MIESZKANIEC” dla `ACTIVE`; czerwony „KONTO ZABLOKOWANE” dla `BLOCKED`; czerwony „KONTO WYGASŁE / WYMELDOWANE” dla `CHECKED_OUT`. |
| **FR-CARD-04** | Weryfikacja wzrokowa na portierni | MUST | Układ i czytelność karty są zoptymalizowane pod kątem szybkiej (w 2-3 sekundy) weryfikacji wzrokowej przez pracownika portierni przy wejściu do budynku, bez konieczności skanowania czy operowania na komputerze. |

---

## 4. Moduł Rezerwacji Pralni (LAUNDRY)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :--- | :--- |
| **FR-LAUND-01** | Konfiguracja parametrów pralni przez Administrację | MUST | Administrator Akademika definiuje zasoby pralnicze dla swojego obiektu: listę pralek, godziny dostępności pralni (np. 07:00–23:00) oraz jednostkowy czas trwania slotu rezerwacyjnego (np. 90 min, 120 min), co zapewnia pełną elastyczność między różnymi domami studenckimi. |
| **FR-LAUND-02** | Przeglądanie grafiku dostępności pralek | MUST | Mieszkaniec ma wgląd w interaktywną siatkę slotów czasowych na dany dzień (z podglądem do 7 dni w przód). Sloty oznaczone są kolorystycznie: wolny, zajęty, moja rezerwacja, wyłączony z użytku (awaria). |
| **FR-LAUND-03** | Rezerwacja slotu pralki | MUST | Użytkownik rezerwuje wolny slot czasowy na konkretną pralkę. System waliduje regułę: maksymalnie do 7 dni w przód oraz maksymalnie 2 aktywne rezerwacje przypadające na danego mieszkańca w bieżącym tygodniu. |
| **FR-LAUND-04** | Anulowanie rezerwacji przez studenta | MUST | Mieszkaniec może anulować swoją rezerwację przed jej rozpoczęciem, co natychmiast uwalnia slot w harmonogramie dla innych zainteresowanych. |
| **FR-LAUND-05** | Pulpit Portierni – wydawanie kluczy i obsługa spóźnień | MUST | Recepcjonista posiada dedykowany widok bieżących rezerwacji do weryfikacji prawa do odbioru klucza do pralni. Portier ma możliwość zwolnienia (anulowania) slotu, jeżeli student nie odbierze klucza w ciągu 15 minut od planowanego startu. |
| **FR-LAUND-06** | Wyłączenie pralki z eksploatacji (Awaria) | MUST | Portier lub Administrator może natychmiast oznaczyć konkretną pralkę jako uszkodzoną (stan `OUT_OF_ORDER`). Akcja ta natychmiast blokuje nowe rezerwacje, kaskadowo anuluje wszystkie aktywne przyszłe rezerwacje tej pralki (status `CANCELLED_MACHINE_OUT_OF_ORDER`), wysyła powiadomienia e-mail do poszkodowanych mieszkańców oraz automatycznie tworzy zgłoszenie awarii w rejestrze usterek (moduł `ISSUES`, status `NEW`, pilność `URGENT`) dla konserwatora. |

---

## 5. Moduł Rezerwacji Salek Tematycznych (ROOMS)

*Moduł opracowany bezpośrednio w oparciu o „Regulamin korzystania z salek tematycznych na terenie Osiedla Studenckiego Politechniki Krakowskiej” (Zarządzenie Rektora PK).*

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :--- | :--- |
| **FR-ROOM-01** | Katalog i parametryzacja salek per DS | MUST | Administrator Domu Studenckiego (ADS) ma możliwość definiowania salek tematycznych w swoim obiekcie wraz z ich specyfikacją: nazwa, typ salki (standardowa, Kujon, Chillout, klubowa), godziny otwarcia (domyślnie 6:00–23:30), maksymalny limit osób oraz opis wyposażenia. |
| **FR-ROOM-02** | Obsługa specyficznych typów salek | MUST | System respektuje ograniczenia regulaminowe dla poszczególnych rodzajów salek: <br>• **Salki standardowe** (Funzone, Bilard, Tenis stołowy, TV, Audio-Video): rezerwacja jednorazowo na max 4 godziny; limit do 10 osób (Audio-Video: do 14 osób). <br>• **Salka do cichej nauki („Kujon”)**: rezerwacja na max 4 godziny; limit do 16 osób; reguła zachowania ciszy. <br>• **Salka „Chillout”**: rezerwacja w przedziale godzinowym 14:00 – 02:00 dnia następnego (cisza nocna po 23:00); limit do 30 osób; zwrot klucza do 10:00 rano. |
| **FR-ROOM-03** | Formularz rezerwacji Organizatora (§2 ust. 2) | MUST | Przy rezerwacji salki mieszkaniec staje się formalnym Organizatorem. System automatycznie pobiera imię, nazwisko, DS, pokój i telefon studenta oraz wymaga zadeklarowania planowanej liczby uczestników (walidacja względem pojemności salki) i celu rezerwacji. |
| **FR-ROOM-04** | Oświadczenie o odpowiedzialności i regulaminie | MUST | Przed potwierdzeniem rezerwacji użytkownik musi obowiązkowo zaakceptować Regulamin salek tematycznych, Regulamin Osiedla Studenckiego oraz cennik szkód (oświadczenie o odpowiedzialności materialnej i porządkowej Organizatora). |
| **FR-ROOM-05** | Reguła 15 minut na portierni (§2 ust. 5) | MUST | Rezerwacja zostaje automatycznie (scheduler) lub ręcznie anulowana przez pracownika recepcji, jeżeli Organizator nie zgłosi się po odbiór klucza w ciągu 15 minut od ustalonej godziny rozpoczęcia rezerwacji. |
| **FR-ROOM-06** | Wniosek o przedłużenie rezerwacji | COULD | Opcjonalnie: mieszkaniec w trakcie rezerwacji może złożyć wniosek o przedłużenie, o ile brak kolizji i nie przekroczono limitu godzinowego salki. Poza krytyczną ścieżką MVP. |
| **FR-ROOM-07** | Ewidencja kar i czarna lista salek (§6 ust. 2) | MUST | Administrator/Kierownik DS ma możliwość nałożenia blokady na konto mieszkańca pozbawiającej go prawa rezerwacji salek tematycznych na okres od 1 do 3 miesięcy (np. za pozostawienie nieporządku, zakłócanie ciszy nocnej lub zniszczenia), z automatycznym zablokowaniem możliwości tworzenia nowych rezerwacji w systemie. |
| **FR-ROOM-08** | Anulowanie rezerwacji salki przez Organizatora | SHOULD | Organizator może anulować własną rezerwację salki **przed** jej rozpoczęciem, co uwalnia slot. Po starcie slotu anulowanie mieszkańca nie jest wymagane w MVP (obsługa przez portiernię / regułę 15 min). |

---

## 6. Moduł Zgłaszania i Obsługi Usterek (ISSUES)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :--- | :--- |
| **FR-ISSUE-01** | Formularz zgłoszenia usterki | MUST | Mieszkaniec zgłasza usterkę, wybierając: zasięg (Mój pokój – numer pobierany automatycznie z profilu, lub Część wspólna: kuchnia piętrowa, węzeł sanitarny, korytarz, pralnia, winda, inne), kategorię problemu (Hydraulika, Elektryka, Meble/Stolarka, Ślusarka/Zamki, Inne), stopień pilności (Normalny / Awaria pilna – np. wyciek wody) oraz szczegółowy opis tekstowy. |
| **FR-ISSUE-02** | Załączanie dokumentacji fotograficznej | MUST | Możliwość dołączenia zdjęcia usterki bezpośrednio z aparatu telefonu lub pamięci urządzenia (przesyłane i bezpiecznie przechowywane w MinIO S3). |
| **FR-ISSUE-03** | Cyfrowy rejestr usterek dla Recepcji (Pulpit Portiera) | MUST | Centralna lista zgłoszeń z podziałem na statusy, z możliwością filtrowania po dacie, kategorii, numerze pokoju, piętrze i stopniu pilności. Portier może przeglądać zgłoszenia, podglądać załączone zdjęcia oraz aktualizować statusy. |
| **FR-ISSUE-04** | Cykl życia i statusy naprawy | MUST | Zgłoszenie przechodzi przez stany domenowe: `NEW` -> `ASSIGNED_TO_MAINTENANCE` -> `IN_PROGRESS` -> `RESOLVED` lub `REJECTED`/`PARTS_REQUIRED` (UI: Nowe → Przekazane konserwatorowi → W trakcie naprawy → Naprawione / Odrzucone / Wymaga części). Przy zmianie statusu portier może wpisać **jedną bieżącą notatkę zwrotną** (`staff_notes` — nadpisywana, bez osobnej tabeli historii komentarzy). Zmiana statusu generuje asynchroniczne powiadomienie e-mail (Mailpit/SMTP) do zgłaszającego. |
| **FR-ISSUE-05** | Śledzenie stanu zgłoszenia przez mieszkańca | MUST | Student ma listę **własnych zgłoszeń** (historia spraw) z podglądem **aktualnego statusu** oraz **ostatniej notatki** portiera. Pełny dziennik wszystkich historycznych zmian statusu jest poza zakresem MVP. |
| **FR-ISSUE-06** | Generowanie listy zadań dla konserwatora | SHOULD | Portier ma możliwość wygenerowania czytelnego zestawienia/wydruku (widok do druku lub PDF) aktywnych zleceń naprawczych do przekazania konserwatorowi rozpoczynającemu dyżur. |

---

## 7. Moduł Tablicy Ogłoszeń i Pomocy Sąsiedzkiej (BOARD)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-BOARD-01** | Publikacja ogłoszeń sąsiedzkich | SHOULD | Mieszkaniec może opublikować post, określając: tytuł, treść, kategorię tematyczną („Pożyczę / Pomoc”, „Kupię / Sprzedam / Oddam”, „Zgubiono / Znaleziono”, „Pytanie ogólne”) oraz zasięg widoczności (cały kampus OSPK: `scope = 'CAMPUS'` lub wyłącznie mój akademik: `scope = 'DORMITORY'`). Publikacja następuje bezpośrednio (brak wieloetapowego workflow zatwierdzania / brak premoderacji w MVP), w oparciu o pełną jawność i identyfikowalność tożsamości autora (FR-BOARD-05). Moduł realizowany w Etapie 4 planu (po bytówce). |
| **FR-BOARD-02** | Filtrowanie feedu ogłoszeń | SHOULD | Użytkownik może filtrować feed ogłoszeń po kategorii, zasięgu (ogólny/mój DS) oraz statusie (aktywne vs rozwiązane). Pełnotekstowe wyszukiwanie treści jest poza zakresem MVP. |
| **FR-BOARD-03** | Wątki dyskusyjne (Komentarze) | SHOULD | Mieszkańcy mogą dodawać publiczne komentarze pod postem, co umożliwia szybką odpowiedź na zapytanie sąsiedzkie (np. „Mogę pożyczyć sól, wpadnij do pokoju 312”). |
| **FR-BOARD-04** | Zamykanie spraw (Oznaczanie jako rozwiązane) | SHOULD | Autor ogłoszenia ma możliwość oznaczenia posta jako „Rozwiązany / Nieaktualny”, co wizualnie wyszarza post i ukrywa go z domyślnego widoku aktywnych ogłoszeń. |
| **FR-BOARD-05** | Identyfikowalność i brak anonimowości | SHOULD | Każdy post i komentarz zawiera imię i nazwisko autora oraz numer pokoju i DS, co zapewnia kulturę wypowiedzi i buduje zaufanie sąsiedzkie. |
| **FR-BOARD-06** | Moderacja treści | SHOULD | Administrator właściwego Domu Studenckiego (ADS) ma uprawnienia do moderacji i usuwania wpisów oraz komentarzy naruszających regulamin osiedla w obrębie swojego DS (`scope = 'DORMITORY'`). Wpisy o zasięgu ogólnokampusowym (`scope = 'CAMPUS'`) podlegają reaktywnej moderacji i usuwaniu zarówno przez Superadmina (AOS), jak i przez kierowników ADS wszystkich akademików. |

---

## 8. Moduł Kalendarza i Oficjalnych Komunikatów (EVENTS)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-EVENT-01** | Publikacja oficjalnych i dyżurnych komunikatów | MUST | Administrator Domu Studenckiego (ADS) oraz Recepcjonista/Portier mogą publikować ogłoszenia techniczno-organizacyjne dla swojego DS (np. wymiana pościeli wg §27 pkt 10 Regulaminu OS PK, awaria sieci, przerwa w ciepłej wodzie w danym DS; `dormitory_id = ID_DS`), natomiast Superadmin (AOS) posiada uprawnienia do publikowania oficjalnych komunikatów i alertów o zasięgu ogólnokampusowym (`dormitory_id = NULL`, np. globalne wyłączenia mediów na osiedlu, zarządzenia OS PK, imprezy centralne). Komunikaty tworzone są z priorytetem `INFO` / `WARNING` / `CRITICAL` (UI: Informacja, Ostrzeżenie, Awaria krytyczna). |
| **FR-EVENT-02** | Baner ważnych ogłoszeń (Alert banner) | MUST | Komunikaty o priorytecie `CRITICAL` z flagą `is_pinned=TRUE` są wyróżnione banerem u góry aplikacji mieszkańca, dopóki pozostają przypięte i aktywne czasowo (`event_date`/`end_date`). **Brak osobnego potwierdzania/odczytu per użytkownik** w MVP — schowanie banera następuje po odpięciu lub wygaśnięciu komunikatu przez personel. |
| **FR-EVENT-03** | Kalendarz życia kampusu | MUST | Interaktywny widok kalendarza (miesiąc / tydzień / dzień) prezentujący oficjalne terminy i komunikaty administracji o zasięgu danego DS (`dormitory_id = user.dormitory_id`) oraz ogólnokampusowym (`dormitory_id IS NULL`), w tym wymianę pościeli, awarie i ogłoszenia ADS/AOS/portierni. |
| **FR-EVENT-04** | Inicjatywy i wydarzenia mieszkańców | COULD | Opcjonalnie: mieszkaniec może dodać proste wydarzenie integracyjne widoczne w kalendarzu **bez** workflow zatwierdzania i **bez** limitu/listy uczestników w MVP. |

---

## 9. Moduł Panelu Portierni i Administracji (ADMIN/PORTAL)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-PORTAL-01** | Pulpit Dyżurnego Portierni (Dashboard Recepcji) | MUST | Centralny ekran dla pracownika portierni prezentujący w czasie rzeczywistym: <br>• Kto ma bieżący slot na pralkę (z przyciskiem rejestracji wydania/zwrotu klucza), <br>• Kto ma bieżący slot na salkę tematyczną (z nazwiskiem Organizatora i tel.), <br>• Licznik i rejestr otwartych usterek w toku naprawy. |
| **FR-PORTAL-02** | Obsługa wydawania i zwrotu kluczy | MUST | Jedno kliknięcie przez portiera rejestruje pobranie lub zwrot klucza do pralni / salki tematycznej wraz ze znacznikiem czasowym. |
| **FR-PORTAL-03** | Konfiguracja zasobów akademika przez ADS | MUST | Administrator Domu Studenckiego konfiguruje infrastrukturę swojego DS: dodawanie pralek, salek, definiowanie godzin dostępności, długości slotów czasowych oraz limitów osób. |
| **FR-PORTAL-04** | Zarządzanie bazą mieszkańców i meldunkami | MUST | Weryfikacja i akceptacja nowo zarejestrowanych studentów, przypisywanie do pokoi, możliwość blokowania konta oraz wprowadzania kar regulaminowych (pozbawienie prawa rezerwacji na 1–3 miesiące). |
| **FR-PORTAL-05** | Zarządzanie obiektami kampusowymi i komunikacją (Superadmin) | MUST | Superadmin ma możliwość dodawania i edycji akademików wchodzących w skład Osiedla Studenckiego PK (DS1, DS2, DS3, DS4, DS B-1), zarządzania kontami kierowników ADS, publikacji oficjalnych komunikatów ogólnokampusowych w module EVENTS oraz moderacji wpisów o zasięgu kampusowym w module BOARD. |

---

## 10. Wymagania Pozafunkcjonalne (NFR)

| Identyfikator | Kategoria | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **NFR-PERF-01** | Wydajność (Performance) | MUST | Czas odpowiedzi backendu dla operacji odczytu (grafiki rezerwacji, feed ogłoszeń) nie może przekraczać 200 ms przy normalnym obciążeniu sieci lokalnej. |
| **NFR-CONC-01** | Współbieżność i integralność | MUST | Mechanizmy transakcyjne na poziomie PostgreSQL i Spring Data JPA muszą gwarantować odporność na wyścigi (Race Condition) – wykluczone jest zarezerwowanie tego samego zasobu w tym samym slocie przez dwóch użytkowników. |
| **NFR-SEC-01** | Bezpieczeństwo sesji | MUST | Bezstanowe uwierzytelnianie oparte na JWT z podpisem kryptograficznym i TTL. Token przekazywany w nagłówku `Authorization: Bearer` (nie w cookie HttpOnly) — ochrona CSRF typu cookie-sesyjnego nie jest wymagana; kluczowe jest zabezpieczenie przed XSS (CSP, escapowanie). |
| **NFR-SEC-02** | Ochrona poświadczeń | MUST | Hasła użytkowników przechowywane w bazie w postaci skrótów generowanych bezpiecznym algorytmem haszującym BCrypt z soleniem. |
| **NFR-SEC-03** | Bezpieczeństwo plików | MUST | Upload zdjęć (awatar, usterka) podlega ścisłej walidacji typu MIME (wyłącznie JPEG, PNG, WebP) oraz limitowi rozmiaru (max 5 MB). Pliki zapisywane w prywatnych bucketach MinIO (`pkampus-issues`, `pkampus-avatars`); serwowanie przez backend / URL podpisany, bez publicznego anonymous download. |
| **NFR-SEC-04** | Szyfrowanie transmisji sieciowej (TLS/HTTPS) | MUST | Komunikacja zewnętrzna zabezpieczona protokołem TLS 1.3 wymuszanym przez bramę Nginx Reverse Proxy wraz z nagłówkiem HSTS i politykami ochrony HTTP (CSP, X-Frame-Options, X-Content-Type-Options). |
| **NFR-SEC-05** | Izolacja sieciowa bazy danych i magazynu | MUST | Baza PostgreSQL oraz MinIO S3 nie publikują żadnych otwartych portów na interfejsie publicznym serwera; komunikują się wyłącznie w wydzielonej sieci wirtualnej Docker bridge (`pkampus-net`). |
| **NFR-SEC-06** | Ochrona przed XSS i injection | MUST | Backend stosuje parametryzowane zapytania JPA/SQL (ochrona przed SQL Injection). Frontend i API stosują escapowanie danych wyjściowych oraz CSP na bramie Nginx (ochrona przed XSS). |
| **NFR-USAB-01** | Responsywność i mobilność (RWD / PWA) | MUST | Interfejs mieszkańca zrealizowany w architekturze Progressive Web App (PWA) zoptymalizowany pod smartfony (Mobile-First, instalacja na ekranie głównym, obsługa aparatu fotograficznego do zgłaszania usterek); interfejs portierni i panelu administracyjnego zoptymalizowany pod ekrany desktopowe i tablety. |
| **NFR-A11Y-01** | Dostępność (WCAG 2.1 AA — zakres MVP) | SHOULD | Widoki krytyczne (logowanie, rezerwacje, karta, usterki, pulpit portierni) spełniają wybrane kryteria WCAG 2.1 poziomu AA: kontrast tekstu, widoczny fokus, etykiety formularzy, nawigacja klawiaturą. Pełny audit AA całej aplikacji jest poza zakresem MVP. |
| **NFR-REL-01** | Niezawodność i samonaprawa usług | MUST | Długowieczne usługi rdzeniowe (`pkampus-proxy`, `pkampus-backend`, `pkampus-db`, `pkampus-minio`, `pkampus-mailpit`) mają `restart: unless-stopped` oraz `healthcheck`. Kontener jednorazowy `pkampus-minio-init` (job init) jest wyłączony z tego wymogu. |
| **NFR-REL-02** | Trwałość danych (Data Persistence) | MUST | Stan relacyjny bazy PostgreSQL oraz magazynu obiektowego MinIO jest w pełni odseparowany od cyklu życia kontenerów poprzez nazwane wolumeny Dockera (`pg_data`, `minio_data`), co zabezpiecza dane przed utratą podczas aktualizacji i restartów. |
| **NFR-DEP-01** | Wdrożenie i konteneryzacja (IaC) | MUST | Całość systemu (frontend React PWA, brama Nginx, backend Spring Boot, baza PostgreSQL, storage MinIO, serwer pocztowy Mailpit) jest w 100% spakowana i uruchamiana jednym poleceniem `docker compose up -d`. |

---

## 11. Reguły Biznesowe (BR)

| Identyfikator | Nazwa reguły | Treść reguły biznesowej |
| :--- | :--- | :--- |
| **BR-01** | Okno i limit rezerwacji pralni | Mieszkaniec może rezerwować pralkę maksymalnie na 7 dni w przód i posiadać w danym tygodniu maksymalnie 2 aktywne rezerwacje. |
| **BR-02** | Reguła 15 minut przy pobieraniu klucza | Zgodnie z §2 ust. 5 Regulaminu salek: jeśli uprawniony mieszkaniec nie odbierze klucza z recepcji w ciągu 15 minut od startu slotu, rezerwacja zostaje anulowana, a zasób uwolniony. |
| **BR-03** | Ramy czasowe salek tematycznych | Salki standardowe oraz salka do nauki („Kujon”) mogą być rezerwowane na jednorazowy czas maksymalnie do 4 godzin (w godzinach 6:00–23:30). Salka „Chillout” funkcjonuje w dedykowanym oknie 14:00–02:00 dnia następnego (maksymalny czas trwania: 12 godzin; cisza nocna po 23:00). |
| **BR-04** | Odpowiedzialność Organizatora | Rezerwujący salkę staje się Organizatorem odpowiadającym materialnie i porządkowo za salę, gości oraz przestrzeganie ciszy nocnej (23:00–06:00 w budynku). |
| **BR-05** | Blokady regulaminowe (Czarna lista) | Na wniosek ADS/KOS (§6 ust. 2 Regulaminu) student, który dopuścił się dewastacji, nieporządku lub złamania regulaminu, może zostać zablokowany w możliwości rezerwacji salek na okres od 1 do 3 miesięcy. |
| **BR-06** | Wymóg aktywacji konta | Konto ze statusem `PENDING_APPROVAL` nie może tworzyć rezerwacji ani zgłaszać usterek do momentu weryfikacji tożsamości i zatwierdzenia meldunku przez Administratora Domu Studenckiego (ADS). |
| **BR-07** | Identyfikacja mieszkańców | Każdy wpis na tablicy ogłoszeń i zgłoszenie usterki jest jednoznacznie podpisane tożsamością mieszkańca (brak anonimowości). |
| **BR-08** | Zwrot klucza salki nocnej Chillout (§3 ust. 7) | W przypadku rezerwacji salki „Chillout” kończącej się o 02:00 Organizator powinien zwrócić klucz na portiernię do godziny 10:00 (czas lokalny akademika). W MVP reguła jest **egzekwowana operacyjnie przez portiernię** (odmowa wydania kolejnego klucza / wniosek o sankcję `ROOM_BAN`); system rejestruje jedynie `key_issued_at` / `key_returned_at` bez automatycznego schedulera blokady. |
