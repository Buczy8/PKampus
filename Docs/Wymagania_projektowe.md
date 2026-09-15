# Analiza Wymagań Projektowych - System PKampus

Dokument specyfikacji wymagań funkcjonalnych, pozafunkcjonalnych i reguł biznesowych dla systemu PKampus (praca inżynierska).

---

## 1. Wstęp i Konwencja Oznaczeń
* **FR (Functional Requirement):** Wymaganie funkcjonalne
* **NFR (Non-Functional Requirement):** Wymaganie pozafunkcjonalne
* **BR (Business Rule):** Reguła biznesowa
* **Priorytetyzacja MoSCoW (48 FR: 24 MUST / 17 SHOULD / 7 COULD ≈ 50/35/15%):**
  * **MUST (24):** Bezwzględne jądro prototypu MVP (rejestracja, weryfikacja meldunku, logowanie RBAC, dynamiczna karta on-line, rezerwacja pralni i salek z regułą 15 min, zgłaszanie awarii ze zdjęciem MinIO, rejestr portierni, mechanizmy anty-race PostgreSQL i konteneryzacja).
  * **SHOULD (17):** Istotne rozszerzenia drugiego etapu (tablica ogłoszeń i pomoc sąsiedzka, baner komunikatów dyżurnych, wyłączanie awaryjne maszyn/salek, procedura resetu hasła, ewidencja kar regulaminowych ROOM_BAN).
  * **COULD (7):** Funkcjonalności opcjonalne (przedłużanie rezerwacji, komentarze sąsiedzkie, lista zadań konserwatora do druku, centralny panel nadzoru miasteczka AOS i konta ADS, kalendarz inicjatyw studenckich).
  * **WON'T:** Bezwzględnie wyłączone z zakresu (siłownie i obiekty sportowe z osobnymi regulaminami, Klub „Piwnica” z trybem płatnym/mailowym, płatności online PayU/Stripe/BLIK, natywne aplikacje iOS/Android, powiadomienia SMS/push, wielopoziomowe komentarze, komunikator 1-na-1, integracja IoT/RFID/elektrozamki, integracja USOS API — por. Charter §4 Out of Scope).
* **Relacja ról systemowych (RBAC) do pojęć Regulaminu OS PK:**
  * **Mieszkaniec (`RESIDENT`):** Student PK zameldowany w DS PK z aktywnym kontem.
  * **Recepcjonista / Portier (`RECEPTIONIST`):** Pracownik portierni/recepcji obsługujący fizyczne wydawanie kluczy oraz cyfrowy zeszyt usterek.
  * **ADS – Administrator Domu Studenckiego (`DORM_ADMIN`):** Rola systemowa reprezentująca Kierownika DS oraz pracowników Administracji Domu Studenckiego (w regulaminie ADS to jednostka organizacyjna pod kierownictwem Kierownika DS). Zarządza zasobami, personelem portierni i weryfikacją meldunków danego akademika.
  * **AOS – Administrator Osiedla Studenckiego / Superadmin (`SUPER_ADMIN`):** Rola systemowa reprezentująca Kierownika Osiedla Studenckiego oraz pion Administracji Osiedla Studenckiego (centralna jednostka osiedla). Posiada uprawnienia nadrzędne dla całego miasteczka akademickiego.
  * **KOS – Komisja Osiedla Studenckiego:** Organ Samorządu Studenckiego PK reprezentujący mieszkańców. W fazie MVP KOS nie posiada odrębnego konta technicznego w aplikacji; jego regulaminowa rola wnioskodawcy lub organu opiniującego (np. przy nakładaniu sankcji wg BR-05 / §6 ust. 2) realizowana jest w procedurze organizacyjnej, a wynikowe blokady wprowadza do systemu administrator ADS (`DORM_ADMIN`).

---

## 2. Moduł Uwierzytelniania i Zarządzania Kontami (AUTH)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-AUTH-01** | Rejestracja mieszkańca i weryfikacja e-mail | MUST | Użytkownik rejestruje się podając: imię, nazwisko, adres e-mail, hasło, numer telefonu, wybiera akademik z listy, podaje numer pokoju oraz załącza zdjęcie twarzy (do karty mieszkańca). Deklarowany numer pokoju oraz identyfikator akademika zapisywane są w profilu użytkownika (`users.declared_room_number`, `users.dormitory_id`). System wysyła link aktywacyjny w celu potwierdzenia adresu e-mail (`PENDING_EMAIL`). Link zawiera **krótkotrwały, podpisany token** (np. JWT/HMAC, TTL ok. 24 h) weryfikowany kryptograficznie po stronie API — **bez osobnej tabeli tokenów w bazie** (w odróżnieniu od resetu hasła). |
| **FR-AUTH-02** | Weryfikacja meldunku (Aktywacja przez Administratora DS) | MUST | Po potwierdzeniu adresu e-mail konto otrzymuje status `PENDING_APPROVAL`. **W MVP wymagany jest panel ADS** (`UC-AUTH-03`) do weryfikacji danych studenta i deklarowanego numeru pokoju (`declared_room_number`) z uczelnianą listą meldunkową, aktywacji konta (`ACTIVE` + rekord w `room_assignments`) albo odrzucenia wniosku (usunięcie konta tymczasowego i awatara z MinIO + e-mail; `ADR-01`). Zgodnie z `BR-06` do momentu aktywacji student widzi ekran informacyjny (Onboarding lock) bez dostępu do rezerwacji i zgłoszeń usterek. Blokady kont i sankcje `ROOM_BAN` nie należą do tego wymagania — por. `FR-PORTAL-04` / `FR-ROOM-07` (SHOULD). |
| **FR-AUTH-03** | Uwierzytelnianie JWT | MUST | Logowanie za pomocą adresu e-mail i hasła. Po poprawnym uwierzytelnieniu system zwraca token JWT zawierający identyfikator użytkownika, przypisany akademik, numer pokoju oraz role (`RESIDENT`, `RECEPTIONIST`, `DORM_ADMIN`, `SUPER_ADMIN`). Konta w stanie `BLOCKED` lub `CHECKED_OUT` nie mogą uzyskać tokenu (HTTP 403 Forbidden). |
| **FR-AUTH-04** | Kontrola dostępu oparta na rolach (RBAC) | MUST | Dostęp do poszczególnych endpointów API oraz widoków frontendowych jest ściśle ograniczony do posiadanych ról. |
| **FR-AUTH-05** | Zarządzanie personelem portierni | SHOULD | Administrator Akademika ma możliwość tworzenia, edycji i dezaktywacji kont z rolą `RECEPTIONIST` dla swojego akademika (w MVP konta początkowe mogą być zasilane skryptem seed). |
| **FR-AUTH-06** | Edycja profilu i zmiana hasła | SHOULD | Użytkownik ma możliwość zmiany hasła oraz aktualizacji danych kontaktowych (np. numeru telefonu). Zmiana numeru pokoju lub akademika wymaga zatwierdzenia przez Administratora Domu Studenckiego (ADS). |
| **FR-AUTH-07** | Procedura resetowania hasła (e-mail token) | SHOULD | Użytkownik ma możliwość zresetowania zapomnianego hasła poprzez podanie adresu e-mail. System generuje jednorazowy token kryptograficzny (TTL: 15 minut) przesyłany linkiem e-mail (Mailpit/SMTP). Użycie linku otwiera formularz zdefiniowania nowego hasła, po czym token ulega natychmiastowemu unieważnieniu (`ADR-07`). |
| **FR-AUTH-08** | Zarządzanie kontami Administratorów DS (AOS) | COULD | Superadmin (AOS) posiada uprawnienia do tworzenia, edycji i dezaktywacji kont kierowników domów studenckich z rolą `DORM_ADMIN` (w MVP konta kierowników mogą być zainicjalizowane w migracji początkowej). |

---

## 3. Moduł Wirtualnej Karty Mieszkańca (CARD)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :--- | :--- |
| **FR-CARD-01** | Wyświetlanie karty mieszkańca | MUST | Dedykowany, pełnoekranowy widok w aplikacji (wzorowany na dokumencie tożsamości mObywatel), prezentujący: zdjęcie mieszkańca, imię i nazwisko, nazwę akademika, numer pokoju, rok akademicki oraz oficjalną pieczęć graficzną. |
| **FR-CARD-02** | Mechanizm dynamiczny on-line i test dotykowy (anty-screenshot / anty-screen-recording) | MUST | Karta wymaga aktywnego połączenia sieciowego z backendem (`/api/v1/profile/card`). Zawiera pobierany z serwera zegar czasu rzeczywistego (godzina, minuta, sekundy) oraz animowany gradient CSS (hologram wizualny). W celu wykluczenia nagrań wideo ekranu (screen recording playback) aplikacja implementuje **interaktywny test dotykowy (Touch Challenge)**: dotknięcie ekranu generuje natychmiastowy dynamiczny efekt fali (ripple effect) ze znacznikiem mikrosekund serwera oraz **dynamiczny kod/kolor dnia (Daily Verification Code/Color)** derywowany bezstanowo (`HMAC_SHA256(JWT_SECRET, current_date)`, bez tabeli w DB — por. ERD §1 pkt 8) i wyświetlany na ekranie dyżurnym portierni. W przypadku braku łączności aplikacja blokuje widok karty (komunikat: „Brak sieci — weryfikacja dynamiczna wymaga połączenia z serwerem. Okazanie karty offline jest nieważne”). |
| **FR-CARD-03** | Dynamiczny wskaźnik statusu ważności | MUST | Karta weryfikuje status konta w bazie PostgreSQL: zielony „AKTYWNA / MIESZKANIEC” dla `ACTIVE`. W przypadku zmiany statusu na `BLOCKED` lub `CHECKED_OUT` w trakcie trwania sesji aplikacja natychmiast unieważnia kartę i wyświetla pełnoekranową czerwoną planszę ostrzegawczą: „KONTO ZABLOKOWANE” lub „KONTO WYGASŁE / WYMELDOWANE”. Próba logowania użytkownika z takim statusem jest odrzucana kodem HTTP 403 Forbidden. |
| **FR-CARD-04** | Weryfikacja wzrokowa i interaktywna na portierni | MUST | Układ i czytelność karty są zoptymalizowane pod kątem szybkiej (w 2-3 sekundy) weryfikacji wzrokowej przez pracownika portierni przy wejściu do budynku (zgodność koloru dnia, animacji i zegara). W razie wątpliwości portier żąda tapnięcia w ekran (test dotykowy ripple) lub okazania fizycznej legitymacji studenckiej, bez konieczności wprowadzania danych do komputera. |

---

## 4. Moduł Rezerwacji Pralni (LAUNDRY)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-LAUND-01** | Konfiguracja parametrów pralni przez Administrację | SHOULD | Administrator Akademika definiuje zasoby pralnicze dla swojego obiektu: listę pralek, godziny dostępności pralni (np. 07:00–23:00) oraz jednostkowy czas trwania slotu rezerwacyjnego (domyślnie 90 min wg DDL). W MVP parametry początkowe zasilane są schematem bazy, a interfejs edycji stanowi rozszerzenie etapu 2. |
| **FR-LAUND-02** | Przeglądanie grafiku dostępności pralek | MUST | Mieszkaniec ma wgląd w interaktywną siatkę slotów czasowych na dany dzień (z podglądem do 7 dni w przód). Sloty oznaczone są kolorystycznie: wolny, zajęty, moja rezerwacja, wyłączony z użytku (awaria). |
| **FR-LAUND-03** | Rezerwacja slotu pralki | MUST | Użytkownik rezerwuje wolny slot czasowy na konkretną pralkę. System waliduje regułę: maksymalnie do 7 dni w przód oraz maksymalnie 2 aktywne rezerwacje (`CONFIRMED` lub `KEY_ISSUED`) przypadające na danego mieszkańca w bieżącym tygodniu kalendarzowym (`BR-01`). |
| **FR-LAUND-04** | Anulowanie rezerwacji przez studenta | MUST | Mieszkaniec może anulować swoją rezerwację przed jej rozpoczęciem, co natychmiast uwalnia slot w harmonogramie dla innych zainteresowanych. |
| **FR-LAUND-05** | Pulpit Portierni – wydawanie kluczy i obsługa spóźnień | MUST | Recepcjonista posiada dedykowany widok bieżących rezerwacji do weryfikacji prawa do odbioru klucza do pralni. Portier lub automatyczny scheduler ma możliwość zwolnienia (anulowania) slotu, jeżeli student nie odbierze klucza w ciągu 15 minut od planowanego startu (`BR-02`, status `AUTO_CANCELLED_15MIN`). |
| **FR-LAUND-06** | Wyłączenie pralki z eksploatacji (Awaria) | SHOULD | Portier lub Administrator może natychmiast oznaczyć konkretną pralkę jako uszkodzoną (stan `OUT_OF_ORDER`). Akcja ta natychmiast blokuje nowe rezerwacje, kaskadowo anuluje wszystkie aktywne przyszłe rezerwacje tej pralki (status `CANCELLED_MACHINE_OUT_OF_ORDER`), wysyła powiadomienia e-mail do poszkodowanych mieszkańców oraz automatycznie tworzy zgłoszenie awarii w rejestrze usterek (moduł `ISSUES`, status `NEW`, pilność `URGENT`) dla konserwatora (`ADR-06`). |

---

## 5. Moduł Rezerwacji Salek Tematycznych (ROOMS)

*Moduł opracowany bezpośrednio w oparciu o „Regulamin korzystania z salek tematycznych na terenie Osiedla Studenckiego Politechniki Krakowskiej” (Zarządzenie Rektora PK).*

*Zakres dostępu i rezerwacji w MVP:* Choć §1 ust. 1 Regulaminu przyznaje prawo korzystania z salek wszystkim mieszkańcom Osiedla Studenckiego PK, w fazie MVP – z przyczyn organizacyjnych, logistyki fizycznego wydawania kluczy przez recepcję oraz kontroli tożsamości na właściwej portierni – prawo rezerwacji salek zostaje zawężone wyłącznie do mieszkańców zameldowanych w danym Domu Studenckim (`user.dormitory_id == thematic_room.dormitory_id`). Międzyakademikowa rezerwacja salek stanowi opcję rozwoju systemu po wdrożeniu centralnych systemów kontroli dostępu (RFID/IoT).

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-ROOM-01** | Katalog i parametryzacja salek per DS | MUST | Administrator Domu Studenckiego (ADS) definiuje salki tematyczne w swoim obiekcie wraz z ich specyfikacją: nazwa, typ salki (`STANDARD`, `QUIET_STUDY_KUJON`, `CHILLOUT`), godziny otwarcia, maksymalny limit osób oraz opis wyposażenia. Mieszkańcy mają dostęp do katalogu salek swojego akademika. |
| **FR-ROOM-02** | Obsługa specyficznych typów salek | SHOULD | System respektuje ograniczenia regulaminowe dla poszczególnych rodzajów salek: <br>• **Salki standardowe** (Funzone, Bilard, Tenis stołowy, TV, Audio-Video): rezerwacja jednorazowo na max 4 godziny; limit do 10 osób (Audio-Video: do 14 osób). <br>• **Salka do cichej nauki („Kujon”)**: rezerwacja na max 4 godziny; limit do 16 osób; reguła zachowania ciszy. <br>• **Salka „Chillout”**: rezerwacja w przedziale godzinowym 14:00 – 02:00 dnia następnego (`spans_midnight = TRUE`, cisza nocna po 23:00); limit do 30 osób; zwrot klucza do 10:00 rano (`BR-08`). |
| **FR-ROOM-03** | Formularz rezerwacji Organizatora (§2 ust. 2) | MUST | Przy rezerwacji salki mieszkaniec staje się formalnym Organizatorem. System automatycznie pobiera imię, nazwisko, DS, pokój i telefon studenta oraz wymaga zadeklarowania planowanej liczby uczestników (walidacja względem pojemności salki) i celu rezerwacji. |
| **FR-ROOM-04** | Oświadczenie o odpowiedzialności i regulaminie | MUST | Przed potwierdzeniem rezerwacji użytkownik musi obowiązkowo zaakceptować Regulamin salek tematycznych, Regulamin Osiedla Studenckiego oraz cennik szkód (oświadczenie o odpowiedzialności materialnej i porządkowej Organizatora). |
| **FR-ROOM-05** | Reguła 15 minut na portierni (§2 ust. 5) | MUST | Rezerwacja zostaje automatycznie (scheduler) lub ręcznie anulowana przez pracownika recepcji, jeżeli Organizator nie zgłosi się po odbiór klucza w ciągu 15 minut od ustalonej godziny rozpoczęcia rezerwacji (`BR-02`, status `AUTO_CANCELLED_15MIN`). |
| **FR-ROOM-06** | Wniosek o przedłużenie rezerwacji | COULD | Opcjonalnie: mieszkaniec w trakcie rezerwacji (status `KEY_ISSUED`, gdy do końca slotu < 30 min) może złożyć wniosek o przedłużenie, o ile brak kolizji i nie przekroczono limitu godzinowego salki (`UC-ROOM-03`). Poza krytyczną ścieżką MVP. |
| **FR-ROOM-07** | Ewidencja kar i czarna lista salek (§6 ust. 2) | SHOULD | Administrator Domu Studenckiego (ADS / Kierownik DS) ma możliwość zarejestrowania blokady konta mieszkańca (`ROOM_BAN`) na okres od 1 do 3 miesięcy (np. za pozostawienie nieporządku, zakłócanie ciszy nocnej lub zniszczenia). W MVP wieloetapowa procedura regulaminowa (§6 ust. 2: wniosek ADS/KOS oraz porozumienie z KOS i Koordynatorem ds. bezpieczeństwa) prowadzona jest w trybie tradycyjnym/kancelaryjnym poza systemem, natomiast w PKampus rejestrowana jest ostateczna prawomocna decyzja wraz z uzasadnieniem (`reason`) oraz wysyłane jest oficjalne powiadomienie e-mail do ukaranego (`UC-ADM-03`). Sankcja `ROOM_BAN` ma zasięg ogólnokampusowy (`BR-05`). |
| **FR-ROOM-08** | Anulowanie rezerwacji salki przez Organizatora | SHOULD | Organizator może anulować własną rezerwację salki **przed** jej rozpoczęciem, co uwalnia slot. Po starcie slotu anulowanie mieszkańca nie jest wymagane w MVP (obsługa przez portiernię / regułę 15 min). |
| **FR-ROOM-09** | Wyłączenie salki z eksploatacji (Remont / Awaria) | SHOULD | Portier lub Administrator Domu Studenckiego (ADS) ma możliwość wyłączenia salki z użytku (przestawienie w stan `MAINTENANCE` z powodu awarii wyposażenia lub prac remontowych). Operacja blokuje tworzenie nowych rezerwacji, kaskadowo anuluje aktywne przyszłe rezerwacje salki (status `CANCELLED_ROOM_MAINTENANCE`), wysyła powiadomienia e-mail do poszkodowanych Organizatorów oraz automatycznie rejestruje zgłoszenie w module `ISSUES` (analogicznie do FR-LAUND-06 dla pralek, `ADR-06`). |

---

## 6. Moduł Zgłaszania i Obsługi Usterek (ISSUES)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-ISSUE-01** | Zgłaszanie usterek technicznych (Mieszkaniec i Personel) | MUST | Mieszkaniec zgłasza usterkę, wybierając: zasięg (Mój pokój – numer pobierany automatycznie z profilu, lub Część wspólna: kuchnia piętrowa, węzeł sanitarny, korytarz, pralnia, winda, inne), kategorię problemu (`PLUMBING`, `ELECTRICAL`, `FURNITURE`, `LOCKSMITH`, `OTHER`), stopień pilności (`NORMAL`, `URGENT`) oraz szczegółowy opis tekstowy (`BR-07`). Ponadto zgłoszenie w części wspólnej może zostać zarejestrowane bezpośrednio przez personel (Portier, ADS) lub wygenerowane automatycznie przez system w ścieżce wyłączenia awaryjnego (FR-LAUND-06, FR-ROOM-09). |
| **FR-ISSUE-02** | Załączanie dokumentacji fotograficznej | MUST | Możliwość dołączenia zdjęcia usterki bezpośrednio z aparatu telefonu lub pamięci urządzenia (przesyłane i bezpiecznie przechowywane w prywatnym buckecie MinIO S3, formaty JPEG/PNG/WebP do 5 MB wg `NFR-SEC-03`). |
| **FR-ISSUE-03** | Cyfrowy rejestr usterek dla Recepcji (Pulpit Portiera) | MUST | Centralna lista zgłoszeń z podziałem na statusy, z możliwością filtrowania po dacie, kategorii, numerze pokoju, piętrze i stopniu pilności. Portier może przeglądać zgłoszenia, podglądać załączone zdjęcia oraz aktualizować statusy. |
| **FR-ISSUE-04** | Cykl życia i statusy naprawy | MUST | Zgłoszenie przechodzi przez stany domenowe DDL: `NEW` -> `ASSIGNED_TO_MAINTENANCE` -> `IN_PROGRESS` -> `RESOLVED` lub `REJECTED`/`PARTS_REQUIRED`. Przy zmianie statusu portier może wpisać **jedną bieżącą notatkę zwrotną** (`staff_notes` — nadpisywana, bez osobnej tabeli historii komentarzy wg `ADR-09`). Zmiana statusu generuje asynchroniczne (`@Async`, `ADR-05`) powiadomienie e-mail (Mailpit/SMTP) do zgłaszającego. |
| **FR-ISSUE-05** | Śledzenie stanu zgłoszenia przez mieszkańca | MUST | Student ma listę **własnych zgłoszeń** (historia spraw) z podglądem **aktualnego statusu** oraz **ostatniej notatki** portiera (`staff_notes`). Pełny dziennik wszystkich historycznych zmian statusu jest poza zakresem MVP. |
| **FR-ISSUE-06** | Generowanie listy zadań dla konserwatora | COULD | Portier ma możliwość wygenerowania czytelnego zestawienia/wydruku (widok do druku lub PDF) aktywnych zleceń naprawczych do przekazania konserwatorowi rozpoczynającemu dyżur. Wygenerowanie listy automatycznie przestawia zaznaczone zgłoszenia w `ASSIGNED_TO_MAINTENANCE` (`UC-ISSUE-04`). |

---

## 7. Moduł Tablicy Ogłoszeń i Pomocy Sąsiedzkiej (BOARD)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-BOARD-01** | Publikacja ogłoszeń sąsiedzkich | SHOULD | Mieszkaniec może opublikować post, określając: tytuł, treść, kategorię (`BORROW_HELP` = „Pożyczę / Pomoc”, `BUY_SELL` = „Kupię / Sprzedam / Oddam”, `LOST_FOUND` = „Zgubiono / Znaleziono”, `GENERAL` = „Pytanie ogólne”) oraz zasięg (`scope='CAMPUS'` z `dormitory_id=NULL` lub `scope='DORMITORY'` z DS autora). Publikacja bezpośrednia (brak premoderacji w MVP), w oparciu o jawność tożsamości autora (`FR-BOARD-05`). Etap 4 planu (po bytówce). |
| **FR-BOARD-02** | Filtrowanie feedu ogłoszeń | SHOULD | Użytkownik może filtrować feed ogłoszeń po kategorii, zasięgu (ogólny/mój DS) oraz statusie (aktywne vs rozwiązane). Pełnotekstowe wyszukiwanie treści jest poza zakresem MVP. |
| **FR-BOARD-03** | Wątki dyskusyjne (Komentarze) | COULD | Mieszkańcy mogą dodawać publiczne komentarze pod postem, co umożliwia szybką odpowiedź na zapytanie sąsiedzkie (np. „Mogę pożyczyć sól, wpadnij do pokoju 312”). |
| **FR-BOARD-04** | Zamykanie i usuwanie spraw przez autora | SHOULD | Autor ogłoszenia ma możliwość oznaczenia posta jako „Rozwiązany / Nieaktualny” (`status='RESOLVED'`), co wizualnie wyszarza post i ukrywa go z domyślnego widoku aktywnych ogłoszeń, oraz miękkiego usunięcia własnego wpisu (`is_deleted=TRUE`, `UC-BOARD-04`). |
| **FR-BOARD-05** | Identyfikowalność i ochrona prywatności | SHOULD | Każdy post i komentarz jednoznacznie identyfikuje autora (`BR-07`): dla zasięgu akademika (`scope = 'DORMITORY'`) prezentowane jest imię, nazwisko oraz numer pokoju i DS; dla zasięgu kampusowego (`scope = 'CAMPUS'`) prezentowane jest imię, nazwisko oraz **wyłącznie nazwa akademika** (**BEZ ujawniania numeru pokoju**), co chroni prywatność i bezpieczeństwo fizyczne mieszkańców na forum całego miasteczka akademickiego. |
| **FR-BOARD-06** | Moderacja treści | SHOULD | Administrator właściwego Domu Studenckiego (ADS) ma uprawnienia do moderacji i usuwania wpisów oraz komentarzy naruszających regulamin osiedla w obrębie swojego DS (`scope = 'DORMITORY'`). Wpisy o zasięgu ogólnokampusowym (`scope = 'CAMPUS'`) podlegają reaktywnej moderacji i usuwaniu zarówno przez Superadmina (AOS), jak i przez kierowników ADS wszystkich akademików. |

---

## 8. Moduł Kalendarza i Oficjalnych Komunikatów (EVENTS)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-EVENT-01** | Publikacja oficjalnych i dyżurnych komunikatów | SHOULD | ADS oraz Portier publikują ogłoszenia dla swojego DS (`dormitory_id=ID_DS`, kategorie `BED_LINEN` = pościel §27 pkt 10, `TECHNICAL_OUTAGE`, `ADMIN_NOTICE`), a Superadmin (AOS) komunikaty ogólnokampusowe (`dormitory_id=NULL`, jw. + zarządzenia/imprezy centralne). Priorytet `INFO`/`WARNING`/`CRITICAL` (UI: Informacja, Ostrzeżenie, Awaria krytyczna). |
| **FR-EVENT-02** | Baner ważnych ogłoszeń (Alert banner) | SHOULD | Komunikaty o priorytecie `CRITICAL` z flagą `is_pinned=TRUE` są wyróżnione banerem u góry aplikacji mieszkańca, dopóki pozostają przypięte i aktywne czasowo (`event_date`/`end_date`). **Brak osobnego potwierdzania/odczytu per użytkownik** w MVP — schowanie banera następuje po odpięciu lub wygaśnięciu komunikatu przez personel (`ADR-08`). |
| **FR-EVENT-03** | Kalendarz życia kampusu | COULD | Interaktywny widok kalendarza (miesiąc / tydzień / dzień) prezentujący oficjalne terminy i komunikaty administracji o zasięgu danego DS (`dormitory_id = user.dormitory_id`) oraz ogólnokampusowym (`dormitory_id IS NULL`), w tym wymianę pościeli, awarie i ogłoszenia ADS/AOS/portierni. |
| **FR-EVENT-04** | Inicjatywy i wydarzenia mieszkańców | COULD | Opcjonalnie: mieszkaniec może dodać proste wydarzenie integracyjne (`category='STUDENT_EVENT'`, `priority='INFO'`) widoczne w kalendarzu **bez** workflow zatwierdzania i **bez** limitu/listy uczestników w MVP. |

---

## 9. Moduł Panelu Portierni i Administracji (ADMIN/PORTAL)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-PORTAL-01** | Pulpit Dyżurnego Portierni (Dashboard Recepcji) | MUST | Centralny ekran dla pracownika portierni prezentujący w czasie rzeczywistym: <br>• Kto ma bieżący slot na pralkę (z przyciskiem rejestracji wydania/zwrotu klucza), <br>• Kto ma bieżący slot na salkę tematyczną (z nazwiskiem Organizatora i tel.), <br>• Licznik i rejestr otwartych usterek w toku naprawy. |
| **FR-PORTAL-02** | Obsługa wydawania i zwrotu kluczy | MUST | Jedno kliknięcie przez portiera rejestruje wydanie klucza (przejście rezerwacji w status `KEY_ISSUED` i zapis `key_issued_at`) lub fizyczny zwrot klucza (przejście rezerwacji w status `COMPLETED` i zapis `key_returned_at`) do pralni / salki tematycznej wraz ze znacznikiem czasowym (`BR-09`). |
| **FR-PORTAL-03** | Konfiguracja zasobów akademika przez ADS | MUST | Administrator Domu Studenckiego konfiguruje infrastrukturę swojego DS w zakresie **salek tematycznych**: dodawanie salek, definiowanie godzin dostępności, długości slotów oraz limitów osób (MUST, `FR-ROOM-01`, `UC-ADM-02`). **Pralki i parametry slotów pralni** (`FR-LAUND-01`, `UC-ADM-01`) w MVP są zasilane seedem/migracją bazy — pełny interfejs edycji pralni jest rozszerzeniem SHOULD etapu 2 i **nie** wchodzi w zakres MUST tego wymagania. |
| **FR-PORTAL-04** | Zarządzanie bazą mieszkańców, blokady i sankcje | SHOULD | Przegląd kont mieszkańców DS, **blokowanie/odblokowywanie** konta (`BLOCKED`/`ACTIVE`) oraz wprowadzanie kar regulaminowych `ROOM_BAN` (1–3 miesiące; por. `FR-ROOM-07`, `UC-ADM-03`). **Weryfikacja meldunku (aktywacja/odrzucenie `PENDING_APPROVAL`) jest MUST** i należy wyłącznie do `FR-AUTH-02` / `UC-AUTH-03` — nie jest odroczona wraz z tym wymaganiem SHOULD. |
| **FR-PORTAL-05** | Zarządzanie obiektami kampusowymi i komunikacją (Superadmin) | COULD | Superadmin ma możliwość dodawania i edycji akademików wchodzących w skład Osiedla Studenckiego PK (DS1, DS2, DS3, DS4, DS B-1), zarządzania kontami kierowników ADS, publikacji oficjalnych komunikatów ogólnokampusowych w module EVENTS oraz moderacji wpisów o zasięgu kampusowym w module BOARD. |

---

## 10. Wymagania Pozafunkcjonalne (NFR)

| Identyfikator | Kategoria | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **NFR-PERF-01** | Wydajność (Performance) | MUST | Czas odpowiedzi backendu dla operacji odczytu (grafiki rezerwacji, feed ogłoszeń) nie może przekraczać 200 ms przy normalnym obciążeniu sieci lokalnej. |
| **NFR-CONC-01** | Współbieżność i integralność | MUST | Mechanizmy transakcyjne na poziomie PostgreSQL i Spring Data JPA muszą gwarantować odporność na wyścigi (Race Condition) – wykluczone jest zarezerwowanie tego samego zasobu w tym samym slocie przez dwóch użytkowników (`EXCLUDE USING gist`, `ADR-02`). |
| **NFR-SEC-01** | Bezpieczeństwo sesji | MUST | Bezstanowe uwierzytelnianie oparte na JWT z podpisem kryptograficznym i TTL 15 min. Token przekazywany w nagłówku `Authorization: Bearer` (nie w cookie HttpOnly) — ochrona CSRF typu cookie-sesyjnego nie jest wymagana; kluczowe jest zabezpieczenie przed XSS (CSP, escapowanie). Natychmiastowe unieważnianie przy blokadzie/wymeldowaniu/zmianie roli przez in-memory blacklist (Caffeine, TTL = 15 min) weryfikowaną w filtrze Spring Security; rate-limit logowania (Bucket4j, per IP). |
| **NFR-SEC-02** | Ochrona poświadczeń | MUST | Hasła użytkowników przechowywane w bazie w postaci skrótów BCrypt z soleniem (12 rund). Polityka złożoności: min. 8 znaków, wielka litera, cyfra, znak specjalny (walidowana w `UC-AUTH-01/04/05`). |
| **NFR-SEC-03** | Bezpieczeństwo plików | MUST | Upload zdjęć (awatar, usterka) podlega ścisłej walidacji typu MIME (wyłącznie JPEG, PNG, WebP) oraz limitowi rozmiaru (max 5 MB). Pliki zapisywane w prywatnych bucketach MinIO (`pkampus-issues`, `pkampus-avatars`, `ADR-04`); serwowanie przez backend / URL podpisany, bez publicznego anonymous download. |
| **NFR-SEC-04** | Szyfrowanie transmisji sieciowej (TLS/HTTPS) | MUST | Komunikacja zewnętrzna zabezpieczona protokołem TLS 1.3 wymuszanym przez bramę Nginx Reverse Proxy wraz z nagłówkiem HSTS i politykami ochrony HTTP (CSP, X-Frame-Options, X-Content-Type-Options). W środowisku produkcyjnym (VPS PK) certyfikaty dostarcza Let's Encrypt z automatycznym odnawianiem przez kontener `pkampus-certbot` (profil `production`); w profilu demonstracyjnym dopuszcza się certyfikaty lokalne (self-signed/mkcert) lub bezpieczny kontekst `localhost` dla PWA, co gwarantuje pełną autonomiczność podczas obrony pracy inżynierskiej. |
| **NFR-SEC-05** | Izolacja sieciowa bazy danych i magazynu | MUST | Baza PostgreSQL oraz MinIO S3 nie publikują żadnych otwartych portów na interfejsie publicznym serwera; komunikują się wyłącznie w wydzielonej sieci wirtualnej Docker bridge (`pkampus-net`). |
| **NFR-SEC-06** | Ochrona przed XSS i injection | MUST | Backend stosuje parametryzowane zapytania JPA/SQL (ochrona przed SQL Injection). Frontend i API stosują escapowanie danych wyjściowych oraz CSP na bramie Nginx (ochrona przed XSS). |
| **NFR-USAB-01** | Responsywność i mobilność (RWD / PWA) | MUST | Interfejs mieszkańca zrealizowany w architekturze Progressive Web App (PWA, `ADR-03`) zoptymalizowany pod smartfony (Mobile-First, instalacja na ekranie głównym, obsługa aparatu fotograficznego do zgłaszania usterek); interfejs portierni i panelu administracyjnego zoptymalizowany pod ekrany desktopowe i tablety. |
| **NFR-A11Y-01** | Dostępność (WCAG 2.1 AA — zakres MVP) | SHOULD | Widoki krytyczne (logowanie, rezerwacje, karta, usterki, pulpit portierni) spełniają wybrane kryteria WCAG 2.1 poziomu AA: kontrast tekstu, widoczny fokus, etykiety formularzy, nawigacja klawiaturą. Pełny audit AA całej aplikacji jest poza zakresem MVP. |
| **NFR-REL-01** | Niezawodność i samonaprawa usług | MUST | Długowieczne usługi rdzeniowe (`pkampus-proxy`, `pkampus-backend`, `pkampus-db`, `pkampus-minio`, `pkampus-mailpit`) mają `restart: unless-stopped` oraz `healthcheck`. Kontener jednorazowy `pkampus-minio-init` (job init) jest wyłączony z tego wymogu; `pkampus-certbot` (profil `production`) ma `restart: unless-stopped` bez healthcheck (pętla `renew` co 12h). |
| **NFR-REL-02** | Trwałość danych (Data Persistence) | MUST | Stan relacyjny bazy PostgreSQL oraz magazynu obiektowego MinIO jest w pełni odseparowany od cyklu życia kontenerów poprzez nazwane wolumeny Dockera (`pg_data`, `minio_data`), co zabezpiecza dane przed utratą podczas aktualizacji i restartów. |
| **NFR-REL-03** | Cykliczne zadania w tle (Scheduler) | MUST | Dedykowany proces harmonogramu Spring Boot (`@Scheduled`) uruchamiany jest z częstotliwością co 1 minutę (`cron = "0 * * * * *"`) w celu bezwzględnego egzekwowania reguły 15 minut zwalniania nieodebranych slotów pralni i salek tematycznych oraz unieważniania przedawnionych tokenów. |
| **NFR-DEP-01** | Wdrożenie i konteneryzacja (IaC) | MUST | Całość systemu (frontend React PWA, brama Nginx, backend Spring Boot, baza PostgreSQL, storage MinIO, serwer pocztowy Mailpit) jest w 100% spakowana i uruchamiana jednym poleceniem `docker compose up -d`. |

---

## 11. Reguły Biznesowe (BR)

| Identyfikator | Nazwa reguły | Treść reguły biznesowej |
| :--- | :--- | :--- |
| **BR-01** | Okno i limit rezerwacji pralni | Mieszkaniec może rezerwować pralkę maksymalnie na **7 dni w przód** (`slotStart <= now + 7 days`). W bieżącym **tygodniu kalendarzowym** (poniedziałek 00:00:00 – niedziela 23:59:59, strefa lokalna akademika / `Europe/Warsaw`) może mieć jednocześnie co najwyżej **2 aktywne** rezerwacje pralki, gdzie „aktywne” = `status IN ('CONFIRMED', 'KEY_ISSUED')` i `start_time` wypada w tym tygodniu. Statusy anulowane (`CANCELLED_*`, `AUTO_CANCELLED_15MIN`) oraz `COMPLETED` **nie** wliczają się do limitu. |
| **BR-02** | Reguła 15 minut przy pobieraniu klucza | Zgodnie z §2 ust. 5 Regulaminu salek (oraz decyzją projektową PKampus rozszerzającą tę zasadę na sloty pralni): jeśli uprawniony mieszkaniec nie odbierze klucza z recepcji w ciągu 15 minut od startu slotu, rezerwacja zostaje anulowana, a zasób uwolniony. |
| **BR-03** | Ramy czasowe salek tematycznych | Salki standardowe oraz salka do nauki („Kujon”) mogą być rezerwowane na jednorazowy czas maksymalnie do 4 godzin (w godzinach 6:00–23:30). Salka „Chillout” funkcjonuje w dedykowanym oknie 14:00–02:00 dnia następnego (maksymalny czas trwania: 12 godzin; cisza nocna po 23:00). |
| **BR-04** | Odpowiedzialność Organizatora | Rezerwujący salkę staje się Organizatorem odpowiadającym materialnie i porządkowo za salę, gości oraz przestrzeganie ciszy nocnej (23:00–06:00 w budynku). |
| **BR-05** | Blokady regulaminowe (Czarna lista) | Zgodnie z §6 ust. 2 Regulaminu salek: za dewastację, nieporządek lub naruszenie regulaminu student może zostać pozbawiony prawa rezerwacji salek na okres od 1 do 3 miesięcy. Sankcja (`ROOM_BAN`) obowiązuje **globalnie na poziomie całego Osiedla Studenckiego** (uniemożliwia rezerwację jakiejkolwiek salki w całym systemie). Procedura uzgodnień (wniosek ADS/KOS, porozumienie z KOS i Koordynatorem ds. bezpieczeństwa) odbywa się w tradycyjnej ścieżce administracyjnej uczelni; w MVP system odnotowuje prawomocną decyzję wprowadzoną przez ADS (`sanctions`), blokując rezerwacje na czas trwania sankcji. |
| **BR-06** | Wymóg aktywacji konta | Konto ze statusem `PENDING_APPROVAL` nie może tworzyć rezerwacji ani zgłaszać usterek do momentu weryfikacji tożsamości i zatwierdzenia meldunku przez Administratora Domu Studenckiego (ADS). |
| **BR-07** | Identyfikacja zgłaszających i autorów | Każdy wpis na tablicy ogłoszeń i zgłoszenie usterki jest jednoznacznie powiązane z kontem użytkownika w systemie (`reporter_id` mieszkańca lub pracownika personelu / portiera wyłączającego zasób w ścieżce auto-awarii) — w systemie nie występują zgłoszenia anonimowe. |
| **BR-08** | Zwrot klucza salki nocnej Chillout (§5 ust. 10) | W przypadku rezerwacji salki „Chillout” kończącej się o 02:00 Organizator powinien zwrócić klucz na portiernię do godziny 10:00 (czas lokalny akademika). W MVP reguła jest **egzekwowana operacyjnie przez portiernię** (odmowa wydania kolejnego klucza / wniosek o sankcję `ROOM_BAN`); system rejestruje jedynie `key_issued_at` / `key_returned_at` bez automatycznego schedulera blokady. |
| **BR-09** | Cykl życia rezerwacji i status COMPLETED | Rezerwacja pralni lub salki tematycznej przechodzi w stan `COMPLETED` w momencie zarejestrowania przez pracownika portierni fizycznego zwrotu klucza (zapis `key_returned_at`). W przypadku braku odnotowania zwrotu przed upływem doby operacyjnej (lub do godz. 10:00 rano dla salki Chillout) portiernia wyjaśnia opóźnienie bezpośrednio z mieszkańcem i może złożyć wniosek o nałożenie sankcji dyscyplinarnej. |

---

## 12. Zasady Retencji Danych i Ochrony Prywatności (RODO / Data Retention)

| Obszar Danych | Okres Przechowywania (Retencja) | Mechanizm Realizacji | Uzasadnienie Prawne / Regulaminowe |
| :--- | :--- | :--- | :--- |
| **Konta wymeldowanych (`CHECKED_OUT`)** | Do zakończenia danego roku akademickiego + 30 dni | Automatyczna anonimizacja danych osobowych (zastąpienie imienia/nazwiska ciągiem `Anonim`, wyczyszczenie e-maila i telefonu). | Rozliczenie kaucji i ewentualnych roszczeń finansowych po wymeldowaniu z DS. |
| **Zdjęcia profilowe mieszkańców (`pkampus-avatars`)** | Do momentu wymeldowania lub procedury rocznej anonimizacji konta (`CHECKED_OUT`) | Usunięcie pliku awatara z prywatnego bucketu MinIO S3 natychmiast po zmianie statusu konta na `CHECKED_OUT`. | Zasada minimalizacji danych (art. 5 ust. 1 lit. c RODO) — brak podstaw do dalszego przetwarzania biometrycznego wizerunku po opuszczeniu akademika. |
| **Zrealizowane i anulowane rezerwacje** | 90 dni od daty zakończenia slotu | Archiwizacja i agregacja statystyczna, usunięcie powiązań z użytkownikiem. | Okres odwoławczy i audytowy wykorzystania infrastruktury wspólnej. |
| **Zdjęcia usterek w MinIO (`issue_photos`)** | 30 dni od uzyskania statusu `RESOLVED` lub `REJECTED` | Cykliczny proces schedulera usuwający binaria z bucketu S3. Sam rekord usterki w bazie PostgreSQL zachowywany przez 12 miesięcy. | Minimalizacja przestrzeni dyskowej oraz ochrona wizerunku wnętrz mieszkalnych. |
| **Prywatność na Tablicy Ogłoszeń** | Dynamiczne filtrowanie w widoku API | W widoku ogólnokampusowym (`scope = 'CAMPUS'`) numer pokoju jest bezwzględnie maskowany (prezentowane tylko imię, nazwisko i DS). | Ochrona bezpieczeństwa osobistego i prywatności studentów przed osobami z zewnątrz. |
| **Wpisy i komentarze tablicy (`posts`, `comments`)** | 30 dni po oznaczeniu jako `RESOLVED`; 14 dni po soft-delete (`is_deleted = TRUE` lub `REMOVED_MODERATOR`) | Trwałe usuwanie rekordów z bazy przez procedurę schedulera. | Utrzymanie aktualności tablicy sąsiedzkiej i retencja śladu audytowego moderacji. |
| **Tokeny bezpieczeństwa (`password_reset_tokens`)** | 15 minut (TTL) | Usuwane natychmiast po użyciu (`used_at IS NOT NULL`) lub kasowane z bazy przez cron po 24h od wygaśnięcia. | Higiena kryptograficzna i bezpieczeństwo poświadczeń. |
| **Archiwa kopii zapasowych (Backup Retention)** | Dzienne: 14 dni, Tygodniowe: 8 tygodni, Miesięczne: 12 miesięcy | Szyfrowanie symetryczne AES-256 (GPG); rotacja i bezpowrotne niszczenie przedawnionych archiwów przez skrypt `backup.sh`. | Zgodność z procedurami Disaster Recovery i minimalizacja ryzyka wycieku danych historycznych. |
