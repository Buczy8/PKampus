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
| **FR-AUTH-01** | Rejestracja mieszkańca | MUST | Użytkownik rejestruje się podając: imię, nazwisko, adres e-mail, hasło, numer telefonu, wybiera akademik z listy, podaje numer pokoju oraz załącza zdjęcie twarzy (do karty mieszkańca). |
| **FR-AUTH-02** | Weryfikacja konta (Workflow akceptacji) | MUST | Nowo zarejestrowane konto studenta otrzymuje status `PENDING_APPROVAL`. Konto musi zostać zatwierdzone przez Portiera lub Administratora Akademika po porównaniu z listą meldunkową. Dopiero po zatwierdzeniu użytkownik uzyskuje dostęp do rezerwacji i funkcji bytowych. |
| **FR-AUTH-03** | Uwierzytelnianie JWT | MUST | Logowanie za pomocą adresu e-mail i hasła. Po poprawnym uwierzytelnieniu system zwraca token JWT zawierający identyfikator użytkownika, przypisany akademik, numer pokoju oraz role (`RESIDENT`, `RECEPTIONIST`, `DORM_ADMIN`, `SUPER_ADMIN`). |
| **FR-AUTH-04** | Kontrola dostępu oparta na rolach (RBAC) | MUST | Dostęp do poszczególnych endpointów API oraz widoków frontendowych jest ściśle ograniczony do posiadanych ról. |
| **FR-AUTH-05** | Zarządzanie personelem portierni | MUST | Administrator Akademika ma możliwość tworzenia, edycji i dezaktywacji kont z rolą `RECEPTIONIST` dla swojego akademika. |
| **FR-AUTH-06** | Edycja profilu i zmiana hasła | SHOULD | Użytkownik ma możliwość zmiany hasła oraz aktualizacji danych kontaktowych (np. numeru telefonu). Zmiana numeru pokoju lub akademika wymaga ponownej weryfikacji przez portiernię. |

---

## 3. Moduł Wirtualnej Karty Mieszkańca (CARD)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-CARD-01** | Wyświetlanie karty mieszkańca | MUST | Dedykowany, pełnoekranowy widok w aplikacji (wzorowany na dokumencie tożsamości mObywatel), prezentujący: zdjęcie mieszkańca, imię i nazwisko, nazwę akademika, numer pokoju, rok akademicki oraz oficjalną pieczęć graficzną. |
| **FR-CARD-02** | Mechanizm anty-screenshot (Weryfikacja dynamiczna) | MUST | Karta zawiera działający na żywo zegar (godzina, minuta, sekundy) oraz animowany element pulsujący/ruchomy (hologram wizualny), uniemożliwiający posługiwanie się statycznymi zrzutami ekranu przez osoby nieuprawnione. |
| **FR-CARD-03** | Dynamiczny wskaźnik statusu ważności | MUST | Karta wyraźnie sygnalizuje status konta: wyraźny zielony znacznik „AKTYWNA / MIESZKANIEC” lub czerwony „KONTO ZABLOKOWANE / WYGASŁE” w przypadku dezaktywacji przez administrację. |
| **FR-CARD-04** | Weryfikacja wzrokowa na portierni | MUST | Układ i czytelność karty są zoptymalizowane pod kątem szybkiej (w 2-3 sekundy) weryfikacji wzrokowej przez pracownika portierni przy wejściu do budynku, bez konieczności skanowania czy operowania na komputerze. |

---

## 4. Moduł Rezerwacji Pralni (LAUNDRY)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-LAUND-01** | Konfiguracja parametrów pralni przez Administrację | MUST | Administrator Akademika definiuje zasoby pralnicze dla swojego obiektu: listę pralek, godziny dostępności pralni (np. 07:00–23:00) oraz jednostkowy czas trwania slotu rezerwacyjnego (np. 90 min, 120 min), co zapewnia pełną elastyczność między różnymi domami studenckimi. |
| **FR-LAUND-02** | Przeglądanie grafiku dostępności pralek | MUST | Mieszkaniec ma wgląd w interaktywną siatkę slotów czasowych na dany dzień (z podglądem do 7 dni w przód). Sloty oznaczone są kolorystycznie: wolny, zajęty, moja rezerwacja, wyłączony z użytku (awaria). |
| **FR-LAUND-03** | Rezerwacja slotu pralki | MUST | Użytkownik rezerwuje wolny slot czasowy na konkretną pralkę. System waliduje regułę: maksymalnie do 7 dni w przód oraz maksymalnie 2 aktywne rezerwacje przypadające na danego mieszkańca w bieżącym tygodniu. |
| **FR-LAUND-04** | Anulowanie rezerwacji przez studenta | MUST | Mieszkaniec może anulować swoją rezerwację przed jej rozpoczęciem, co natychmiast uwalnia slot w harmonogramie dla innych zainteresowanych. |
| **FR-LAUND-05** | Pulpit Portierni – wydawanie kluczy i obsługa spóźnień | MUST | Recepcjonista posiada dedykowany widok bieżących rezerwacji do weryfikacji prawa do odbioru klucza do pralni. Portier ma możliwość zwolnienia (anulowania) slotu, jeżeli student nie odbierze klucza w ciągu 15 minut od planowanego startu. |
| **FR-LAUND-06** | Wyłączenie pralki z eksploatacji (Awaria) | MUST | Portier lub Administrator może natychmiast oznaczyć konkretną pralkę jako uszkodzoną, co blokuje możliwość dalszych rezerwacji i automatycznie anuluje istniejące rezerwacje (z powiadomieniem użytkowników). |

---

## 5. Moduł Rezerwacji Salek Tematycznych (ROOMS)

*Moduł opracowany bezpośrednio w oparciu o „Regulamin korzystania z salek tematycznych na terenie Osiedla Studenckiego Politechniki Krakowskiej” (Zarządzenie Rektora PK).*

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-ROOM-01** | Katalog i parametryzacja salek per DS | MUST | Administrator Domu Studenckiego (ADS) ma możliwość definiowania salek tematycznych w swoim obiekcie wraz z ich specyfikacją: nazwa, typ salki (standardowa, Kujon, Chillout, klubowa), godziny otwarcia (domyślnie 6:00–23:30), maksymalny limit osób oraz opis wyposażenia. |
| **FR-ROOM-02** | Obsługa specyficznych typów salek | MUST | System respektuje ograniczenia regulaminowe dla poszczególnych rodzajów salek: <br>• **Salki standardowe** (Funzone, Bilard, Tenis stołowy, TV, Audio-Video): rezerwacja jednorazowo na max 4 godziny; limit do 10 osób (Audio-Video: do 14 osób). <br>• **Salka do cichej nauki („Kujon”)**: rezerwacja na max 4 godziny; limit do 16 osób; reguła zachowania ciszy. <br>• **Salka „Chillout”**: rezerwacja w przedziale godzinowym 14:00 – 02:00 dnia następnego (cisza nocna po 23:00); limit do 30 osób; zwrot klucza do 10:00 rano. |
| **FR-ROOM-03** | Formularz rezerwacji Organizatora (§2 ust. 2) | MUST | Przy rezerwacji salki mieszkaniec staje się formalnym Organizatorem. System automatycznie pobiera imię, nazwisko, DS, pokój i telefon studenta oraz wymaga zadeklarowania planowanej liczby uczestników (walidacja względem pojemności salki) i celu rezerwacji. |
| **FR-ROOM-04** | Oświadczenie o odpowiedzialności i regulaminie | MUST | Przed potwierdzeniem rezerwacji użytkownik musi obowiązkowo zaakceptować Regulamin salek tematycznych, Regulamin Osiedla Studenckiego oraz cennik szkód (oświadczenie o odpowiedzialności materialnej i porządkowej Organizatora). |
| **FR-ROOM-05** | Reguła 15 minut na portierni (§2 ust. 5) | MUST | Rezerwacja zostaje automatycznie lub ręcznie anulowana przez pracownika recepcji, jeżeli Organizator nie zgłosi się po odbiór klucza w ciągu 15 minut od ustalonej godziny rozpoczęcia rezerwacji. |
| **FR-ROOM-06** | Wniosek o przedłużenie rezerwacji | SHOULD | Mieszkaniec w trakcie trwania rezerwacji może złożyć w aplikacji wniosek o przedłużenie czasu korzystania z salki, pod warunkiem braku kolizji z kolejnymi rezerwacjami w harmonogramie i nieprzekroczenia godziny 23:30. |
| **FR-ROOM-07** | Ewidencja kar i czarna lista salek (§6 ust. 2) | MUST | Administrator/Kierownik DS ma możliwość nałożenia blokady na konto mieszkańca pozbawiającej go prawa rezerwacji salek tematycznych na okres od 1 do 3 miesięcy (np. za pozostawienie nieporządku, zakłócanie ciszy nocnej lub zniszczenia), z automatycznym zablokowaniem możliwości tworzenia nowych rezerwacji w systemie. |

---

## 6. Moduł Zgłaszania i Obsługi Usterek (ISSUES)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-ISSUE-01** | Formularz zgłoszenia usterki | MUST | Mieszkaniec zgłasza usterkę, wybierając: zasięg (Mój pokój – numer pobierany automatycznie z profilu, lub Część wspólna: kuchnia piętrowa, węzeł sanitarny, korytarz, pralnia, winda, inne), kategorię problemu (Hydraulika, Elektryka, Meble/Stolarka, Ślusarka/Zamki, Inne), stopień pilności (Normalny / Awaria pilna – np. wyciek wody) oraz szczegółowy opis tekstowy. |
| **FR-ISSUE-02** | Załączanie dokumentacji fotograficznej | MUST | Możliwość dołączenia zdjęcia usterki bezpośrednio z aparatu telefonu lub pamięci urządzenia (przesyłane i bezpiecznie przechowywane w MinIO S3). |
| **FR-ISSUE-03** | Cyfrowy rejestr usterek dla Recepcji (Pulpit Portiera) | MUST | Centralna lista zgłoszeń z podziałem na statusy, z możliwością filtrowania po dacie, kategorii, numerze pokoju, piętrze i stopniu pilności. Portier może przeglądać zgłoszenia, podglądać załączone zdjęcia oraz aktualizować statusy. |
| **FR-ISSUE-04** | Cykl życia i statusy naprawy | MUST | Zgłoszenie przechodzi przez określone stany: `NOWE` -> `PRZEKAZANE_KONSERWATOROWI` -> `W_TRAKCIE_NAPRAWY` -> `NAPRAWIONE` lub `ODRZUCONE`/`WYMAGA_CZĘŚCI`. Recepcjonista przy zmianie statusu ma możliwość wpisania komentarza zwrotnego dla mieszkańca (np. informacja o dacie wizyty konserwatora). |
| **FR-ISSUE-05** | Śledzenie stanu zgłoszenia przez mieszkańca | MUST | Student ma wgląd we własną historię zgłoszeń, aktualny status oraz odpowiedzi i notatki wprowadzane przez recepcję. |
| **FR-ISSUE-06** | Generowanie listy zadań dla konserwatora | SHOULD | Portier ma możliwość wygenerowania czytelnego zestawienia/wydruku (widok do druku lub PDF) aktywnych zleceń naprawczych do przekazania konserwatorowi rozpoczynającemu dyżur. |

---

## 7. Moduł Tablicy Ogłoszeń i Pomocy Sąsiedzkiej (BOARD)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-BOARD-01** | Publikacja ogłoszeń sąsiedzkich | MUST | Mieszkaniec może opublikować post, określając: tytuł, treść, kategorię tematyczną („Pożyczę / Pomoc”, „Kupię / Sprzedam / Oddam”, „Zgubiono / Znaleziono”, „Pytanie ogólne”) oraz zasięg widoczności (cały kampus OSPK lub wyłącznie mój akademik). |
| **FR-BOARD-02** | Filtrowanie i wyszukiwanie postów | MUST | Użytkownik może filtrować feed ogłoszeń po kategorii, zasięgu (ogólny/mój DS) oraz statusie (aktywne vs rozwiązane). |
| **FR-BOARD-03** | Wątki dyskusyjne (Komentarze) | MUST | Mieszkańcy mogą dodawać publiczne komentarze pod postem, co umożliwia szybką odpowiedź na zapytanie sąsiedzkie (np. „Mogę pożyczyć sól, wpadnij do pokoju 312”). |
| **FR-BOARD-04** | Zamykanie spraw (Oznaczanie jako rozwiązane) | MUST | Autor ogłoszenia ma możliwość oznaczenia posta jako „Rozwiązany / Nieaktualny”, co wizualnie wyszarza post i ukrywa go z domyślnego widoku aktywnych ogłoszeń. |
| **FR-BOARD-05** | Identyfikowalność i brak anonimowości | MUST | Każdy post i komentarz zawiera imię i nazwisko autora oraz numer pokoju i DS, co zapewnia kulturę wypowiedzi i buduje zaufanie sąsiedzkie. |
| **FR-BOARD-06** | Moderacja treści | SHOULD | Administrator Domu Studenckiego ma uprawnienia do moderacji i usuwania wpisów lub komentarzy naruszających regulamin osiedla. |

---

## 8. Moduł Kalendarza i Oficjalnych Komunikatów (EVENTS)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-EVENT-01** | Publikacja oficjalnych komunikatów administracji | MUST | Kierownictwo ADS/AOS może publikować przypięte ogłoszenia techniczno-organizacyjne (np. awaria sieci internetowej, przerwa w dostawie ciepłej wody, harmonogram cyklicznej wymiany pościeli wg §27 pkt 10 Regulaminu OS PK) z oznaczeniem stopnia ważności (Informacja, Ostrzeżenie, Awaria krytyczna). |
| **FR-EVENT-02** | Baner ważnych ogłoszeń (Alert banner) | MUST | Krytyczne komunikaty administracji są wyróżnione w górnej części aplikacji na telefonie i pulpicie studenta aż do momentu ich potwierdzenia/odczytania. |
| **FR-EVENT-03** | Kalendarz życia kampusu | MUST | Interaktywny widok kalendarza (miesiąc / tydzień / dzień) agregujący terminy oficjalne (wymiana pościeli, zebrania KOS, akcje kwaterunkowe) oraz wydarzenia kulturalno-integracyjne. |
| **FR-EVENT-04** | Inicjatywy i wydarzenia mieszkańców | SHOULD | Zalogowany mieszkaniec może utworzyć otwarte wydarzenie integracyjne (np. wspólne oglądanie meczu w salce TV, turniej tenisa stołowego, sesja planszówek), które po zatwierdzeniu lub bezpośrednio pojawia się w kalendarzu z limitem uczestników. |

---

## 9. Moduł Panelu Portierni i Administracji (ADMIN/PORTAL)

| Identyfikator | Nazwa wymagania | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **FR-PORTAL-01** | Pulpit Dyżurnego Portierni (Dashboard Recepcji) | MUST | Centralny ekran dla pracownika portierni prezentujący w czasie rzeczywistym: <br>• Kto ma bieżący slot na pralkę (z przyciskiem wydania/zwrotu klucza), <br>• Kto ma bieżący slot na salkę tematyczną (z nazwiskiem Organizatora i tel.), <br>• Listę nowych kont studentów oczekujących na zatwierdzenie, <br>• Licznik otwartych usterek. |
| **FR-PORTAL-02** | Obsługa wydawania i zwrotu kluczy | MUST | Jedno kliknięcie przez portiera rejestruje pobranie lub zwrot klucza do pralni / salki tematycznej wraz ze znacznikiem czasowym. |
| **FR-PORTAL-03** | Konfiguracja zasobów akademika przez ADS | MUST | Administrator Domu Studenckiego konfiguruje infrastrukturę swojego DS: dodawanie pralek, salek, definiowanie godzin dostępności, długości slotów czasowych oraz limitów osób. |
| **FR-PORTAL-04** | Zarządzanie bazą mieszkańców i meldunkami | MUST | Weryfikacja i akceptacja nowo zarejestrowanych studentów, przypisywanie do pokoi, możliwość blokowania konta oraz wprowadzania kar regulaminowych (pozbawienie prawa rezerwacji na 1–3 miesiące). |
| **FR-PORTAL-05** | Zarządzanie obiektami kampusowymi (Superadmin) | MUST | Superadmin ma możliwość dodawania i edycji akademików wchodzących w skład Osiedla Studenckiego PK (DS1, DS2, DS3, DS4, DS B-1) oraz zarządzania kontami kierowników ADS. |

---

## 10. Wymagania Pozafunkcjonalne (NFR)

| Identyfikator | Kategoria | Priorytet | Opis wymagania |
| :--- | :--- | :---: | :--- |
| **NFR-PERF-01** | Wydajność (Performance) | MUST | Czas odpowiedzi backendu dla operacji odczytu (grafiki rezerwacji, feed ogłoszeń) nie może przekraczać 200 ms przy normalnym obciążeniu sieci lokalnej. |
| **NFR-CONC-01** | Współbieżność i integralność | MUST | Mechanizmy transakcyjne na poziomie PostgreSQL i Spring Data JPA muszą gwarantować odporność na wyścigi (Race Condition) – wykluczone jest zarezerwowanie tego samego zasobu w tym samym slocie przez dwóch użytkowników. |
| **NFR-SEC-01** | Bezpieczeństwo sesji | MUST | Bezstanowe uwierzytelnianie oparte na standardzie JWT z podpisem kryptograficznym i zdefiniowanym czasem życia tokenu. |
| **NFR-SEC-02** | Ochrona poświadczeń | MUST | Hasła użytkowników przechowywane w bazie w postaci skrótów generowanych bezpiecznym algorytmem haszującym BCrypt z soleniem. |
| **NFR-SEC-03** | Bezpieczeństwo plików | MUST | Upload zdjęć (awatar, usterka) podlega ścisłej walidacji typu MIME (wyłącznie JPEG, PNG, WebP) oraz limitowi rozmiaru (max 5 MB). Pliki zapisywane w izolowanym magazynie MinIO. |
| **NFR-USAB-01** | Responsywność (RWD) | MUST | Interfejs mieszkańca w pełni dostosowany do ekranów smartfonów (Mobile-First); interfejs portierni i panelu administracyjnego zoptymalizowany pod ekrany desktopowe i tablety. |
| **NFR-DEP-01** | Wdrożenie i konteneryzacja | MUST | Całość systemu (frontend React, backend Spring Boot, baza PostgreSQL, storage MinIO) jest w 100% spakowana i uruchamiana jednym poleceniem `docker compose up`. |

---

## 11. Reguły Biznesowe (BR)

| Identyfikator | Nazwa reguły | Treść reguły biznesowej |
| :--- | :--- | :--- |
| **BR-01** | Okno i limit rezerwacji pralni | Mieszkaniec może rezerwować pralkę maksymalnie na 7 dni w przód i posiadać w danym tygodniu maksymalnie 2 aktywne rezerwacje. |
| **BR-02** | Reguła 15 minut przy pobieraniu klucza | Zgodnie z §2 ust. 5 Regulaminu salek: jeśli uprawniony mieszkaniec nie odbierze klucza z recepcji w ciągu 15 minut od startu slotu, rezerwacja zostaje anulowana, a zasób uwolniony. |
| **BR-03** | Ramy czasowe salek tematycznych | Salki standardowe oraz salka do nauki („Kujon”) mogą być rezerwowane na jednorazowy czas maksymalnie do 4 godzin (w godzinach 6:00–23:30). Salka „Chillout” funkcjonuje w oknie 14:00–02:00. |
| **BR-04** | Odpowiedzialność Organizatora | Rezerwujący salkę staje się Organizatorem odpowiadającym materialnie i porządkowo za salę, gości oraz przestrzeganie ciszy nocnej (23:00–06:00 w budynku). |
| **BR-05** | Blokady regulaminowe (Czarna lista) | Na wniosek ADS/KOS (§6 ust. 2 Regulaminu) student, który dopuścił się dewastacji, nieporządku lub złamania regulaminu, może zostać zablokowany w możliwości rezerwacji salek na okres od 1 do 3 miesięcy. |
| **BR-06** | Wymóg aktywacji konta | Konto ze statusem `PENDING_APPROVAL` nie może tworzyć rezerwacji ani zgłaszać usterek do momentu weryfikacji tożsamości i zatwierdzenia przez portiernię/administrację. |
| **BR-07** | Identyfikacja mieszkańców | Każdy wpis na tablicy ogłoszeń i zgłoszenie usterki jest jednoznacznie podpisane tożsamością mieszkańca (brak anonimowości). |
