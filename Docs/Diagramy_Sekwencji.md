# Specyfikacja Diagramów Sekwencji (UML Sequence) - System PKampus

---

### 1. Przyjęte Standardy Notacyjne i Architektoniczne

Zgodnie z zasadami modelowania dynamiki systemów wielowarstwowych (Multi-tier Architecture / MVC):
1. **Warstwy i Linie Życia (Lifelines):**
   * **Aktor:** Reprezentuje użytkownika zewnętrznego systemu (`👤 Mieszkaniec`, `👤 Recepcjonista`, `👤 Administrator DS`) lub proces systemowy (`⏱️ System / Scheduler`).
   * **Frontend (React UI):** Warstwa prezentacji SPA (komponenty React, stan lokalny, obsługa formularzy, żądania HTTP Axios/Fetch).
   * **Kontroler REST (Spring Boot Controller):** Punkt wejściowy API (walidacja DTO `@Valid`, autoryzacja ról `@PreAuthorize`, mapowanie kodów HTTP).
   * **Serwis Biznesowy (Domain / Application Service):** Warstwa logiki biznesowej i zarządzania transakcjami (`@Transactional`, reguły PK, integralność).
   * **Baza Danych (Spring Data JPA / PostgreSQL):** Warstwa utrwalania danych, blokady transakcyjne, ograniczenia unikalności (`UNIQUE constraints`).
   * **Komponenty Zewnętrzne:** Usługi wyspecjalizowane w środowisku kontenerowym Docker Compose:
     * `✉️ Mailpit (SMTP)` – asynchroniczna wysyłka powiadomień e-mail,
     * `🪣 MinIO (S3)` – magazyn obiektowy na pliki multimedialne (zdjęcia usterek, awatary).
2. **Semantyka Komunikatów:**
   * `->>` : Wywołanie synchroniczne (żądanie HTTP, wywołanie metody wewnątrzprocesowej).
   * `-)` : Wywołanie asynchroniczne (np. zadanie w tle `@Async`, delegacja do wątku pocztowego).
   * `-->>` : Komunikat powrotny (powrót sterowania, odpowiedź HTTP, wynik zapytania JPA).
3. **Paski Aktywności (`activate` / `deactivate`):**
   * Precyzyjnie obrazują czas trwania wykonania operacji na danej linii życia.
4. **Numeracja Kroków (`autonumber`):**
   * Automatyczna numeracja komunikatów ułatwiająca śledzenie sekwencji czasowej.
5. **Obsługa Wariantów i Błędów (`alt / else`):**
   * Prezentacja głównego przebiegu optymistycznego (Happy Path) oraz kluczowych scenariuszy odmowy biznesowej lub błędów walidacji.

---

## 2. Sekwencja 1: Dwuetapowa Rejestracja i Aktywacja Meldunku (AUTH & CARD)

Proces obejmuje rejestrację nowego mieszkańca, weryfikację adresu e-mail, przejście konta w stan oczekiwania na potwierdzenie meldunku (`PENDING_APPROVAL`) oraz zatwierdzenie tożsamości przez Administratora DS.

```mermaid
sequenceDiagram
    autonumber
    actor M as 👤 Mieszkaniec
    participant UI as 🖥️ Frontend (React)
    participant CTL as ⚙️ AuthController
    participant SVC as 🧠 AuthService
    participant DB as 🗄️ PostgreSQL (JPA)
    participant MAIL as ✉️ Mailpit (SMTP)
    actor ADS as 👤 Administrator DS

    M->>UI: Wypełnienie formularza rejestracji (dane, pokój, zdjęcie)
    activate UI
    UI->>CTL: POST /api/auth/register (RegisterRequestDTO)
    activate CTL
    CTL->>SVC: registerResident(dto)
    activate SVC

    SVC->>DB: findByEmail(dto.email)
    activate DB
    DB-->>SVC: Wynik wyszukiwania (brak duplikatu)
    deactivate DB

    alt [Adres e-mail jest wolny]
        SVC->>DB: save(nowy użytkownik: PENDING_EMAIL, hash BCrypt)
        activate DB
        DB-->>SVC: Encja User z wygenerowanym ID
        deactivate DB

        SVC-)MAIL: sendVerificationEmail(user.email, token)
        SVC-->>CTL: Rejestracja powiodła się
        CTL-->>UI: 201 Created (Komunikat: Sprawdź skrzynkę e-mail)
        UI-->>M: Wyświetlenie monitu o kliknięcie w link
    else [Adres e-mail już istnieje w bazie]
        SVC-->>CTL: throw UserAlreadyExistsException
        CTL-->>UI: 409 Conflict (Komunikat: E-mail zajęty)
        UI-->>M: Błąd walidacji formularza
    end
    deactivate SVC
    deactivate CTL
    deactivate UI

    %% Etap 2: Potwierdzenie e-mail
    M->>UI: Kliknięcie w link z tokenem aktywacyjnym
    activate UI
    UI->>CTL: GET /api/auth/verify-email?token=xyz
    activate CTL
    CTL->>SVC: verifyEmailToken(token)
    activate SVC
    SVC->>DB: update Status = PENDING_APPROVAL
    activate DB
    DB-->>SVC: Zaktualizowano status
    deactivate DB
    SVC-->>CTL: Token poprawny
    deactivate SVC
    CTL-->>UI: 200 OK (Status: PENDING_APPROVAL)
    deactivate CTL
    UI-->>M: Ekran blokady: "Konto oczekuje na weryfikację meldunku przez ADS"
    deactivate UI

    %% Etap 3: Akceptacja przez ADS
    ADS->>UI: Przegląd listy niezaakceptowanych meldunków
    activate UI
    UI->>CTL: GET /api/admin/pending-residents
    activate CTL
    CTL->>DB: findAllByStatus(PENDING_APPROVAL)
    activate DB
    DB-->>CTL: Lista studentów do weryfikacji
    deactivate DB
    CTL-->>UI: 200 OK (Lista oczekujących)
    deactivate CTL
    UI-->>ADS: Wyświetlenie listy meldunkowej

    ADS->>UI: Kliknięcie „Zatwierdź meldunek” dla mieszkańca
    UI->>CTL: POST /api/admin/residents/{id}/activate
    activate CTL
    CTL->>SVC: activateResidentAccount(id)
    activate SVC
    SVC->>DB: update Status = ACTIVE, Aktywacja Karty Mieszkańca
    activate DB
    DB-->>SVC: Zapisano status ACTIVE
    deactivate DB
    SVC-)MAIL: sendAccountActivatedNotification(user.email)
    SVC-->>CTL: Konto aktywowane
    deactivate SVC
    CTL-->>UI: 200 OK (Konto aktywne)
    deactivate CTL
    UI-->>ADS: Potwierdzenie aktywacji meldunku
    deactivate UI
```

---

## 3. Sekwencja 2: Rezerwacja Pralni z Ochroną Przed Wyścigiem (LAUNDRY & CONCURRENCY)

Proces prezentuje rezerwację slotu czasowego na konkretną pralkę. Zabezpiecza system przed zjawiskiem *Race Condition* (równoległą próbą zajęcia tego samego slotu przez dwóch studentów) za pomocą izolacji transakcji i unikalnych ograniczeń bazodanowych.

```mermaid
sequenceDiagram
    autonumber
    actor M as 👤 Mieszkaniec
    participant UI as 🖥️ Frontend (React)
    participant CTL as ⚙️ LaundryController
    participant SVC as 🧠 LaundryBookingService
    participant DB as 🗄️ PostgreSQL (JPA / Transakcja)

    M->>UI: Wybór pralki i slotu czasowego w siatce grafiku
    activate UI
    UI->>CTL: POST /api/laundry/bookings (pralkaId, slotStart, slotEnd)
    activate CTL
    CTL->>SVC: bookSlot(userId, pralkaId, slotStart, slotEnd)
    activate SVC

    %% Sprawdzenie reguł biznesowych
    SVC->>DB: countActiveBookingsInCurrentWeek(userId)
    activate DB
    DB-->>SVC: Liczba aktywnych rezerwacji mieszkańca
    deactivate DB

    alt [Mieszkaniec ma już >= 2 aktywne rezerwacje w tygodniu]
        SVC-->>CTL: throw BookingLimitExceededException (BR-01)
        CTL-->>UI: 400 Bad Request (Limit 2 rezerwacji na tydzień wyczerpany)
        UI-->>M: Wyświetlenie komunikatu o przekroczeniu limitu
    else [Mieszkaniec spełnia limit tygodniowy]
        SVC->>DB: BEGIN TRANSACTION (SERIALIZABLE / PESSIMISTIC_WRITE)
        activate DB
        SVC->>DB: findConflictingSlot(pralkaId, slotStart, slotEnd)
        DB-->>SVC: Wynik sprawdzenia dostępności

        alt [Slot jest wolny w wybranym przedziale]
            SVC->>DB: INSERT INTO laundry_bookings (status=CONFIRMED, ...)
            DB-->>SVC: Utworzono rezerwację (ID, znacznik czasu)
            SVC->>DB: COMMIT TRANSACTION
            SVC-->>CTL: BookingDTO (Potwierdzona rezerwacja)
            CTL-->>UI: 201 Created (Rezerwacja potwierdzona)
            UI-->>M: Zielony alert sukcesu + podświetlenie kafelka
        else [Slot został w tej samej chwili zajęty przez innego mieszkańca]
            SVC->>DB: ROLLBACK TRANSACTION
            SVC-->>CTL: throw SlotAlreadyBookedException (Wykryto kolizję)
            CTL-->>UI: 409 Conflict (Ten slot został przed chwilą zarezerwowany)
            UI-->>M: Odświeżenie siatki i komunikat o kolizji
        end
        deactivate DB
    end
    deactivate SVC
    deactivate CTL
    deactivate UI
```

---

## 4. Sekwencja 3: Procedura Wydania Klucza i Reguła 15 Minut (LAUNDRY/ROOMS & SCHEDULER)

Zgodnie z §2 ust. 5 Regulaminu Osiedla Studenckiego, mieszkaniec musi odebrać klucz w ciągu 15 minut od startu rezerwacji. Diagram ilustruje dwa alternatywne warianty: pomyślny odbiór klucza na portierni oraz automatyczne zwolnienie zasobu przez harmonogram zadań w tle (`@Scheduled`).

```mermaid
sequenceDiagram
    autonumber
    actor M as 👤 Mieszkaniec
    actor P as 👤 Recepcjonista (Portier)
    participant UI as 🖥️ Pulpit Portierni (React)
    participant CTL as ⚙️ KeyManagementController
    participant SVC as 🧠 KeyService
    participant DB as 🗄️ PostgreSQL (JPA)
    participant SCH as ⏱️ Background Scheduler

    %% Scenariusz A: Pomyślne wydanie klucza
    Note over M, P: SCENARIUSZ A: Mieszkaniec przychodzi na czas (< 15 min)
    M->>P: Pokazanie Wirtualnej Karty Mieszkańca na telefonie
    P->>UI: Podgląd bieżących rezerwacji na dany slot czasowy
    activate UI
    UI-->>P: Karta aktywna, rezerwacja potwierdzona (Mieszkaniec pok. 312)
    P->>UI: Kliknięcie przycisku „Wydaj klucz”
    UI->>CTL: POST /api/keys/issue (bookingId)
    activate CTL
    CTL->>SVC: issueKey(bookingId, receptionistId)
    activate SVC
    SVC->>DB: update Booking SET status='KEY_ISSUED', keyCollectedAt=NOW()
    activate DB
    DB-->>SVC: Zaktualizowano rezerwację
    deactivate DB
    SVC-->>CTL: KeyIssueConfirmationDTO
    deactivate SVC
    CTL-->>UI: 200 OK (Zarejestrowano wydanie klucza)
    deactivate CTL
    UI-->>P: Zmiana statusu kafelka na zielony ("Klucz wydany")
    deactivate UI
    P->>M: Fizyczne wydanie klucza do rąk studenta

    %% Scenariusz B: Minęło 15 minut, brak odbioru klucza
    Note over P, SCH: SCENARIUSZ B: Minęło 15 minut od startu slotu (Reguła §2 ust. 5)
    SCH->>SVC: triggerReleaseUnclaimedSlots() [wyzwalany cyklicznie co 1 min]
    activate SCH
    activate SVC
    SVC->>DB: findUnclaimedSlotsOlderThan(slotStartTime + 15 min, status='CONFIRMED')
    activate DB
    DB-->>SVC: Lista porzuconych rezerwacji (No-Show)
    deactivate DB

    loop Dla każdej porzuconej rezerwacji
        SVC->>DB: update Booking SET status='AUTO_CANCELLED_NO_SHOW'
        activate DB
        DB-->>SVC: Slot uwolniony
        deactivate DB
    end
    SVC-->>SCH: Liczba zwolnionych slotów
    deactivate SVC
    deactivate SCH

    %% Odświeżenie na portierni i u mieszkańców
    UI->>CTL: GET /api/keys/live-status (odpytanie okresowe)
    activate UI
    activate CTL
    CTL->>DB: pobierz aktualny stan grafiku
    activate DB
    DB-->>CTL: Slot pralki/salki oznaczony jako WOLNY
    deactivate DB
    CTL-->>UI: 200 OK (Slot dostępny)
    deactivate CTL
    UI-->>P: Aktualizacja pulpitu: slot oznaczony jako wolny
    deactivate UI
```

---

## 5. Sekwencja 4: Zgłoszenie Awarii z Uploadem Zdjęcia do MinIO S3 (ISSUES)

Proces przedstawia zgłoszenie usterki technicznej przez mieszkańca z dołączeniem dokumentacji fotograficznej przesyłanej do magazynu obiektowego MinIO S3 oraz jej obsługę w warsztacie portierni.

```mermaid
sequenceDiagram
    autonumber
    actor M as 👤 Mieszkaniec
    participant UI as 🖥️ Frontend (React Mobile)
    participant CTL as ⚙️ IssueController
    participant SVC as 🧠 IssueService
    participant S3 as 🪣 MinIO S3 Storage
    participant DB as 🗄️ PostgreSQL (JPA)
    actor P as 👤 Recepcjonista (Portier)

    M->>UI: Wybór lokalizacji, opis usterki, załączenie zdjęcia z aparatu
    activate UI
    UI->>CTL: POST /api/issues (Multipart: IssueFormDTO + photo File)
    activate CTL

    %% Walidacja pliku
    alt [Zdjęcie nie spełnia wymogów: rozmiar > 5MB lub typ MIME != JPEG/PNG/WebP]
        CTL-->>UI: 400 Bad Request (Nieobsługiwany format lub plik za duży)
        UI-->>M: Wyświetlenie komunikatu o błędzie walidacji zdjęcia
    else [Plik poprawny i kompletny]
        CTL->>SVC: createIssueWithAttachment(dto, file, residentId)
        activate SVC

        %% Przesyłanie do MinIO
        SVC->>S3: putObject(bucket="issues", objectKey=UUID.jpg, stream)
        activate S3
        S3-->>SVC: 200 OK (etag, objectUrl)
        deactivate S3

        %% Zapis w bazie
        SVC->>DB: INSERT INTO issues (title, room, photoUrl, status='NOWE')
        activate DB
        DB-->>SVC: Zapisana encja Issue z wygenerowanym ID
        deactivate DB

        SVC-->>CTL: IssueDetailsDTO
        deactivate SVC
        CTL-->>UI: 201 Created (Zgłoszenie przyjęte)
        UI-->>M: Informacja o numerze zgłoszenia i statusie „NOWE”
    end
    deactivate CTL
    deactivate UI

    %% Obsługa zgłoszenia przez portiernię
    P->>UI: Wejście w moduł „Rejestr Usterek”
    activate UI
    UI->>CTL: GET /api/issues/active
    activate CTL
    CTL->>DB: findAllByStatusIn('NOWE', 'PRZEKAZANE')
    activate DB
    DB-->>CTL: Lista awarii z miniaturami zdjęć MinIO
    deactivate DB
    CTL-->>UI: 200 OK (Zgłoszenia)
    deactivate CTL
    UI-->>P: Prezentacja rejestru spraw

    P->>UI: Zmiana statusu na „PRZEKAZANE_KONSERWATOROWI” + notatka
    activate UI
    UI->>CTL: PATCH /api/issues/{id}/status (status, komentarz)
    activate CTL
    CTL->>SVC: updateIssueStatus(id, newStatus, comment)
    activate SVC
    SVC->>DB: UPDATE issues SET status='PRZEKAZANE_KONSERWATOROWI'
    activate DB
    DB-->>SVC: Zapisano zmianę statusu
    deactivate DB
    SVC-->>CTL: Zaktualizowane dane sprawy
    deactivate SVC
    CTL-->>UI: 200 OK
    deactivate CTL
    UI-->>P: Sprawa zaktualizowana na liście
    deactivate UI
```

---

## 6. Sekwencja 5: Rezerwacja Salki z Weryfikacją Czarnej Listy Kar (ROOMS & SANCTIONS)

Proces weryfikuje uprawnienia mieszkańca do rezerwacji salki tematycznej (cicha nauka „Kujon”, Funzone, Chillout) ze szczególnym uwzględnieniem ewidencji kar dyscyplinarnych (§6 ust. 2 Regulaminu: 1–3 miesiące blokady salek).

```mermaid
sequenceDiagram
    autonumber
    actor M as 👤 Mieszkaniec (Organizator)
    participant UI as 🖥️ Frontend (React)
    participant CTL as ⚙️ RoomBookingController
    participant SVC as 🧠 RoomBookingService
    participant SANCT as 🛡️ SanctionService
    participant DB as 🗄️ PostgreSQL (JPA)

    M->>UI: Wybór salki (np. Chillout), przedziału godzin i liczby osób
    activate UI
    M->>UI: Zaznaczenie akceptacji regulaminu i oświadczenia Organizatora
    UI->>CTL: POST /api/rooms/bookings (CreateRoomBookingDTO)
    activate CTL
    CTL->>SVC: reserveRoom(userId, dto)
    activate SVC

    %% Krok 1: Weryfikacja czarnej listy kar regulaminowych
    SVC->>SANCT: checkActiveSanctions(userId, category='ROOM_BAN')
    activate SANCT
    SANCT->>DB: findActiveSanction(userId, currentDate)
    activate DB
    DB-->>SANCT: Wynik weryfikacji sankcji
    deactivate DB
    SANCT-->>SVC: Wynik weryfikacji sankcji (Status kary)
    deactivate SANCT

    alt [Mieszkaniec posiada aktywną karę blokady salek (1-3 mies.)]
        SVC-->>CTL: throw ResidentSanctionBlockedException (§6 ust. 2)
        CTL-->>UI: 403 Forbidden (Twoje konto posiada blokadę rezerwacji salek do YYYY-MM-DD)
        UI-->>M: Czerwony komunikat odmowy z datą wygaśnięcia kary
    else [Brak aktywnych kar dyscyplinarnych]

        %% Krok 2: Walidacja reguł czasowych danej salki
        alt [Przekroczono limit czasu: > 4h dla salki standardowej/Kujon lub poza oknem 14:00-02:00 dla Chillout]
            SVC-->>CTL: throw InvalidRoomTimeWindowException
            CTL-->>UI: 400 Bad Request (Niedozwolony przedział godzinowy dla typu salki)
            UI-->>M: Komunikat błędu: Maksymalny czas to 4h
        else [Parametry zgodne z regulaminem i brak kolizji slotu]
            SVC->>DB: INSERT INTO room_bookings (userId, roomId, status='CONFIRMED', organizerTerms=TRUE)
            activate DB
            DB-->>SVC: Utworzono rezerwację salki
            deactivate DB
            SVC-->>CTL: RoomBookingDetailsDTO
            CTL-->>UI: 201 Created (Rezerwacja pomyślna)
            UI-->>M: Potwierdzenie z przypomnieniem o odbiorze klucza w 15 min
        end
    end
    deactivate SVC
    deactivate CTL
    deactivate UI
```

---

## 7. Sekwencja 6: Publikacja Komunikatu Dyżurnego / Wymiany Pościeli i Odbiór Alertu (BOARD & EVENTS)

Proces obrazuje publikację ważnego komunikatu technicznego lub organizacyjnego (np. cykliczna wymiana pościeli wg §27 pkt 10 Regulaminu OS PK, awaria dostaw ciepłej wody) przez pracownika portierni lub ADS oraz jego natychmiastową prezentację na smartfonie mieszkańca w postaci wyróżnionego banera alertu.

```mermaid
sequenceDiagram
    autonumber
    actor P as 👤 Recepcjonista / ADS
    participant UI_P as 🖥️ Panel Personelu (React)
    participant CTL as ⚙️ EventController
    participant SVC as 🧠 EventService
    participant DB as 🗄️ PostgreSQL (JPA)
    participant UI_M as 📱 Frontend Mieszkańca (Mobile)
    actor M as 👤 Mieszkaniec

    P->>UI_P: Wprowadzenie komunikatu (Tytuł: "Wymiana pościeli", Priorytet: ALERT_CRITICAL, Data)
    activate UI_P
    UI_P->>CTL: POST /api/events/announcements (CreateEventDTO)
    activate CTL
    CTL->>SVC: publishAnnouncement(dto, authorId, dormId)
    activate SVC

    SVC->>DB: INSERT INTO dorm_events (category='BED_LINEN', isPinned=TRUE, priority='CRITICAL')
    activate DB
    DB-->>SVC: Zapisano ogłoszenie urzędowe
    deactivate DB

    SVC-->>CTL: EventPublishedDTO
    deactivate SVC
    CTL-->>UI_P: 201 Created (Ogłoszenie opublikowane)
    deactivate CTL
    UI_P-->>P: Potwierdzenie publikacji w kalendarzu i banerze kampusu
    deactivate UI_P

    %% Odbiór przez mieszkańca
    Note over UI_M, M: Mieszkaniec uruchamia aplikację na smartfonie lub przechodzi między zakładkami
    M->>UI_M: Otwarcie ekranu głównego PKampus
    activate UI_M
    UI_M->>CTL: GET /api/events/active-alerts?dormId={dormId}
    activate CTL
    CTL->>DB: findActivePinnedAlerts(dormId, currentDate)
    activate DB
    DB-->>CTL: Aktywny komunikat o wymianie pościeli (Priorytet CRITICAL)
    deactivate DB
    CTL-->>UI_M: 200 OK (Lista aktywnych alertów)
    deactivate CTL
    UI_M-->>M: Wyświetlenie przypiętego czerwonego banera ostrzegawczego na samej górze ekranu
    deactivate UI_M
```

---

### 8. Podsumowanie Pokrycia Dynamiki

Zaprojektowane diagramy sekwencji pokrywają pełne spektrum zachowań dynamicznych systemu PKampus:
1. **Asynchroniczność i integracja e-mail:** Zastosowanie kolejki zadań asynchronicznych w Spring Boot dla Mailpit.
2. **Bezpieczeństwo transakcyjne:** Blokady bazodanowe i unikalne indeksy wykluczające podwójne rezerwacje w pralniach i salkach.
3. **Automatyzacja procesów w tle:** Dedykowany Spring Scheduler realizujący regułę 15 minut (§2 ust. 5 Regulaminu).
4. **Zarządzanie mediami:** Bezpośrednia integracja backendu z magazynem obiektowym MinIO (S3) przy obsłudze usterek.
5. **Egzekwowanie prawa wewnętrznego PK:** Walidacja czarnej listy kar dyscyplinarnych (§6 ust. 2) przed dopuszczeniem do zasobów.
