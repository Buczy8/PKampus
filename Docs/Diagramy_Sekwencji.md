# Specyfikacja Diagramów Sekwencji (UML Sequence) - System PKampus

---

### 1. Przyjęte Standardy Notacyjne i Architektoniczne

Zgodnie z zasadami modelowania dynamiki systemów wielowarstwowych (Multi-tier Architecture / MVC):
1. **Warstwy i Linie Życia (Lifelines):**
   * **Aktor:** Reprezentuje użytkownika zewnętrznego systemu (`Mieszkaniec`, `Recepcjonista`, `Administrator DS`) lub proces systemowy (`System / Scheduler`).
   * **Frontend (React PWA):** Warstwa prezentacji SPA/PWA (komponenty React, stan lokalny, obsługa formularzy, żądania HTTP Axios/Fetch).
   * **Kontroler REST (Spring Boot Controller):** Punkt wejściowy API (walidacja DTO `@Valid`, autoryzacja ról `@PreAuthorize`, mapowanie kodów HTTP).
   * **Serwis Biznesowy (Domain / Application Service):** Warstwa logiki biznesowej i zarządzania transakcjami (`@Transactional`, reguły PK, integralność).
   * **Baza Danych (Spring Data JPA / PostgreSQL):** Warstwa utrwalania danych, blokady transakcyjne, ograniczenia wykluczające (`EXCLUDE USING gist` / SQLSTATE `23P01`).
   * **Komponenty Zewnętrzne:** Usługi wyspecjalizowane w środowisku kontenerowym Docker Compose:
     * `Mailpit (SMTP)` – asynchroniczna wysyłka powiadomień e-mail,
     * `MinIO (S3)` – magazyn obiektowy na pliki multimedialne (zdjęcia usterek, awatary).
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

Proces obejmuje rejestrację nowego mieszkańca, weryfikację adresu e-mail przez **podpisany, krótkotrwały token w linku** (bez tabeli tokenów aktywacyjnych), przejście konta w stan `PENDING_APPROVAL` oraz zatwierdzenie tożsamości przez Administratora DS wraz z utworzeniem meldunku w `room_assignments`.

```mermaid
sequenceDiagram
    autonumber
    actor M as Mieszkaniec
    participant UI as Frontend (React PWA)
    participant CTL as AuthController
    participant SVC as AuthService
    participant DB as PostgreSQL (JPA)
    participant S3 as MinIO S3 Storage
    participant MAIL as Mailpit (SMTP)
    actor ADS as Administrator DS

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
        SVC->>S3: putObject(bucket="pkampus-avatars", objectKey=UUID.jpg, photoStream)
        activate S3
        S3-->>SVC: 200 OK (avatarUrl)
        deactivate S3

        SVC->>DB: save(nowy użytkownik: PENDING_EMAIL, hash BCrypt, avatar_url)
        activate DB
        DB-->>SVC: Encja User z wygenerowanym ID
        deactivate DB

        SVC-)MAIL: sendVerificationEmail(user.email, signedToken)
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

    %% Etap 2: Potwierdzenie e-mail (token podpisany, bez persystencji w DB)
    M->>UI: Kliknięcie w link z podpisanym tokenem aktywacyjnym
    activate UI
    UI->>CTL: GET /api/auth/verify-email?token=xyz
    activate CTL
    CTL->>SVC: verifySignedEmailToken(token)
    activate SVC
    SVC->>SVC: Walidacja podpisu i TTL tokenu (HMAC/JWT)
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
    SVC->>DB: update Status = ACTIVE
    activate DB
    DB-->>SVC: Zapisano status ACTIVE
    deactivate DB
    SVC->>DB: INSERT INTO room_assignments (user_id, room_id, academic_year, is_active=TRUE, check_in_date=NOW())
    activate DB
    DB-->>SVC: Utworzono aktywny meldunek mieszkańca
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

Proces prezentuje rezerwację slotu czasowego na konkretną pralkę. Zabezpiecza system przed zjawiskiem *Race Condition* za pomocą ograniczenia integralności PostgreSQL `chk_laundry_no_overlap` (`EXCLUDE USING gist`) na przecięciu przedziałów czasowych `tstzrange(start_time, end_time) WITH &&`, odrzucającego równoległe kolizyjne próby rezerwacji kodem błędu SQLSTATE `23P01` (`exclusion_violation`).

```mermaid
sequenceDiagram
    autonumber
    actor M as Mieszkaniec
    participant UI as Frontend (React PWA)
    participant CTL as LaundryController
    participant SVC as LaundryBookingService
    participant DB as PostgreSQL (JPA / Transakcja)

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
        SVC->>DB: INSERT INTO laundry_bookings (machine_id, user_id, start_time, end_time, status='CONFIRMED')
        activate DB

        alt [Brak kolizji przedziałów czasowych - pomyślna ewaluacja chk_laundry_no_overlap]
            DB-->>SVC: Zapisano rezerwację (ID, znacznik czasu)
            SVC-->>CTL: BookingDTO (Potwierdzona rezerwacja)
            CTL-->>UI: 201 Created (Rezerwacja potwierdzona)
            UI-->>M: Zielony alert sukcesu + podświetlenie kafelka
        else [Wykryto kolizję nachodzących slotów - naruszenie chk_laundry_no_overlap]
            DB-->>SVC: Błąd SQLSTATE 23P01 (exclusion_violation)
            SVC-->>CTL: throw SlotAlreadyBookedException (Wykryto kolizję współbieżną)
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
    actor M as Mieszkaniec
    actor P as Recepcjonista (Portier)
    participant UI as Pulpit Portierni (React)
    participant CTL as KeyManagementController
    participant SVC as KeyService
    participant DB as PostgreSQL (JPA)
    participant SCH as Background Scheduler

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
    SVC->>DB: update Booking SET status='KEY_ISSUED', key_issued_at=NOW()
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
        SVC->>DB: update Booking SET status='AUTO_CANCELLED_15MIN'
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
    actor M as Mieszkaniec
    participant UI as Frontend (React PWA)
    participant CTL as IssueController
    participant SVC as IssueService
    participant S3 as MinIO S3 Storage
    participant DB as PostgreSQL (JPA)
    participant MAIL as Mailpit (SMTP)
    actor P as Recepcjonista (Portier)

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
        SVC->>S3: putObject(bucket="pkampus-issues", objectKey=UUID.jpg, stream)
        activate S3
        S3-->>SVC: 200 OK (etag, objectUrl)
        deactivate S3

        %% Zapis w bazie: encja zgłoszenia oraz załącznik 1:N
        SVC->>DB: INSERT INTO issues (reporter_id, dormitory_id, room_id, category, urgency, description, status='NEW')
        activate DB
        DB-->>SVC: Zapisana encja Issue z wygenerowanym ID
        deactivate DB

        SVC->>DB: INSERT INTO issue_photos (issue_id, photo_url, file_name, file_size_bytes)
        activate DB
        DB-->>SVC: Zapisano rekord załącznika fotograficznego
        deactivate DB

        SVC-->>CTL: IssueDetailsDTO
        deactivate SVC
        CTL-->>UI: 201 Created (Zgłoszenie przyjęte)
        UI-->>M: Informacja o numerze zgłoszenia i statusie „NEW” (Nowe)
    end
    deactivate CTL
    deactivate UI

    %% Obsługa zgłoszenia przez portiernię
    P->>UI: Wejście w moduł „Rejestr Usterek”
    activate UI
    UI->>CTL: GET /api/issues/active
    activate CTL
    CTL->>DB: findAllByStatusIn('NEW', 'ASSIGNED_TO_MAINTENANCE')
    activate DB
    DB-->>CTL: Lista awarii z miniaturami zdjęć MinIO
    deactivate DB
    CTL-->>UI: 200 OK (Zgłoszenia)
    deactivate CTL
    UI-->>P: Prezentacja rejestru spraw

    P->>UI: Zmiana statusu na „ASSIGNED_TO_MAINTENANCE” + notatka
    activate UI
    UI->>CTL: PATCH /api/issues/{id}/status (status, komentarz)
    activate CTL
    CTL->>SVC: updateIssueStatus(id, newStatus, comment)
    activate SVC
    SVC->>DB: UPDATE issues SET status='ASSIGNED_TO_MAINTENANCE', staff_notes=comment
    activate DB
    DB-->>SVC: Zapisano zmianę statusu
    deactivate DB

    %% Asynchroniczne powiadomienie e-mail do zgłaszającego mieszkańca
    SVC-)MAIL: sendIssueStatusChangeEmail(student.email, issue.id, newStatus, comment)

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
    actor M as Mieszkaniec (Organizator)
    participant UI as Frontend (React PWA)
    participant CTL as RoomBookingController
    participant SVC as RoomBookingService
    participant SANCT as SanctionService
    participant DB as PostgreSQL (JPA)

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
            SVC->>DB: INSERT INTO room_bookings (user_id, room_id, status='CONFIRMED', terms_accepted=TRUE)
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

Proces obrazuje publikację ważnego komunikatu technicznego lub organizacyjnego przez portiernię lub ADS oraz prezentację banera alertu u mieszkańca dla przypiętych komunikatów `CRITICAL` (bez osobnego potwierdzania odczytu per użytkownik — baner znika po odpięciu/wygaśnięciu).

```mermaid
sequenceDiagram
    autonumber
    actor P as Recepcjonista / ADS
    participant UI_P as Panel Personelu (React)
    participant CTL as EventController
    participant SVC as EventService
    participant DB as PostgreSQL (JPA)
    participant UI_M as Frontend Mieszkańca (React PWA)
    actor M as Mieszkaniec

    P->>UI_P: Wprowadzenie komunikatu (Tytuł: "Wymiana pościeli", Priorytet: CRITICAL, Data)
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

---

## 8. Sekwencja 7: Awaryjne Wyłączenie Pralki z Eksploatacji i Kaskadowe Anulowanie Rezerwacji (LAUNDRY & ISSUES)

Proces przedstawia zgłoszenie awarii pralki przez dyżurnego pracownika recepcji (lub ADS), jej natychmiastowe wyłączenie z eksploatacji w bazie danych (`OUT_OF_ORDER`), kaskadowe anulowanie wszystkich zaplanowanych rezerwacji ze statusem `CANCELLED_MACHINE_OUT_OF_ORDER`, asynchroniczną wysyłkę powiadomień e-mail do poszkodowanych studentów oraz automatyczne wygenerowanie powiązanego zgłoszenia usterki w rejestrze warsztatu (`issues`).

```mermaid
sequenceDiagram
    autonumber
    actor ACT as Portier / Admin DS
    participant UI as Frontend (React Desktop/PWA)
    participant CTL as LaundryController
    participant SVC as LaundryService
    participant ISS as IssueService
    participant DB as PostgreSQL (JPA)
    participant MAIL as Mailpit (SMTP)

    ACT->>UI: Wybór pralki i kliknięcie „Zgłoś awarię / Wyłącz pralkę” (powód: np. Wyciek wody)
    activate UI
    UI->>CTL: POST /api/laundry/machines/{id}/breakdown (MachineBreakdownDTO)
    activate CTL

    CTL->>SVC: reportMachineBreakdown(machineId, dto, reporterId)
    activate SVC

    %% Rozpoczęcie transakcji bazodanowej
    rect rgb(240, 245, 255)
        note over SVC,DB: Transakcja biznesowa (@Transactional)
        
        %% 1. Zmiana statusu pralki
        SVC->>DB: UPDATE laundry_machines SET status='OUT_OF_ORDER', notes=dto.reason WHERE id=machineId
        activate DB
        DB-->>SVC: Zaktualizowano stan pralki (OUT_OF_ORDER)
        deactivate DB

        %% 2. Pobranie przyszłych rezerwacji
        SVC->>DB: findFutureConfirmedBookings(machineId, NOW())
        activate DB
        DB-->>SVC: Lista aktywnych rezerwacji do anulowania (List<Booking>)
        deactivate DB

        %% 3. Kaskadowe anulowanie rezerwacji
        SVC->>DB: UPDATE laundry_bookings SET status='CANCELLED_MACHINE_OUT_OF_ORDER' WHERE machine_id=machineId AND start_time >= NOW() AND status='CONFIRMED'
        activate DB
        DB-->>SVC: Zaktualizowano rezerwacje
        deactivate DB

        %% 4. Automatyczne utworzenie zgłoszenia w module ISSUES
        SVC->>ISS: createAutomatedBreakdownIssue(machine, dto.reason, reporterId)
        activate ISS
        ISS->>DB: INSERT INTO issues (reporter_id, dormitory_id, common_area_name, category='OTHER', urgency='URGENT', description='Awaria pralki...', status='NEW')
        activate DB
        DB-->>ISS: Zapisano usterkę (issue_id)
        deactivate DB
        ISS-->>SVC: Utworzono zgłoszenie serwisowe
        deactivate ISS
    end

    %% Asynchroniczna wysyłka powiadomień e-mail
    loop Dla każdego poszkodowanego mieszkańca
        SVC-)MAIL: sendBreakdownNotificationEmail(student.email, machine.identifier, booking.startTime)
    end

    SVC-->>CTL: BreakdownReportResponseDTO(machineId, cancelledCount, issueId)
    deactivate SVC
    CTL-->>UI: 200 OK (Podsumowanie wyłączenia zasobu)
    deactivate CTL
    UI-->>ACT: Wyświetlenie potwierdzenia: wyłączono pralkę, anulowano rezerwacje, zarejestrowano usterkę
    deactivate UI
```

---

---

## 9. Sekwencja 8: Procedura Resetowania Hasła przez Link z Tokenem E-mail (AUTH & MAIL)

Proces przedstawia bezpieczną ścieżkę odzyskiwania dostępu do konta (`FR-AUTH-07`, `UC-AUTH-07`): zgłoszenie żądania przez mieszkańca, wygenerowanie jednorazowego tokenu kryptograficznego zapisanego w `password_reset_tokens` (TTL: 15 minut), asynchroniczną wysyłkę linku e-mail (Mailpit SMTP) oraz weryfikację tokenu wraz z haszowaniem nowego hasła (BCrypt) i unieważnieniem tokenu.

```mermaid
sequenceDiagram
    autonumber
    actor M as Mieszkaniec
    participant UI as Frontend (React PWA)
    participant CTL as AuthController
    participant SVC as AuthService
    participant DB as PostgreSQL (JPA)
    participant MAIL as Mailpit (SMTP)

    %% Krok 1: Żądanie zresetowania hasła
    M->>UI: Wybór opcji „Zapomniałem hasła” i podanie adresu e-mail
    activate UI
    UI->>CTL: POST /api/auth/forgot-password (ForgotPasswordDTO: email)
    activate CTL
    CTL->>SVC: initiatePasswordReset(email)
    activate SVC

    SVC->>DB: findByEmail(email)
    activate DB
    DB-->>SVC: Encja User (znaleziono użytkownika)
    deactivate DB

    alt [Użytkownik istnieje w systemie]
        SVC->>SVC: Wygenerowanie bezpiecznego tokenu (SecureRandom / UUID)
        SVC->>DB: INSERT INTO password_reset_tokens (user_id, token_hash, expires_at=NOW()+15min)
        activate DB
        DB-->>SVC: Zapisano rekord tokenu
        deactivate DB

        SVC-)MAIL: sendPasswordResetEmail(user.email, resetToken)
    end

    SVC-->>CTL: Procedura zainicjowana
    deactivate SVC
    CTL-->>UI: 200 OK (Komunikat ogólny: Jeśli e-mail istnieje, wysłano link)
    deactivate CTL
    UI-->>M: Informacja: Sprawdź skrzynkę pocztową
    deactivate UI

    %% Krok 2: Otwarcie linku i zmiana hasła
    M->>MAIL: Otwarcie wiadomości i kliknięcie linku resetującego (/reset-password?token=XYZ)
    activate MAIL
    MAIL-->>UI: Przekierowanie do formularza nowego hasła z tokenem
    deactivate MAIL
    activate UI

    M->>UI: Wprowadzenie nowego hasła i zatwierdzenie
    UI->>CTL: POST /api/auth/reset-password (ResetPasswordDTO: token, newPassword)
    activate CTL
    CTL->>SVC: completePasswordReset(dto)
    activate SVC

    SVC->>SVC: hashToken = SHA-256(dto.token)
    SVC->>DB: findValidTokenByHash(hashToken, NOW()) WHERE used_at IS NULL
    activate DB
    DB-->>SVC: Rekord tokenu (status poprawny) / pusty wynik
    deactivate DB

    alt [Token poprawny i aktywny: used_at IS NULL oraz expires_at > NOW]
        SVC->>SVC: Hash nowego hasła (BCrypt z soleniem)
        SVC->>DB: UPDATE users (hash) oraz UPDATE password_reset_tokens (used_at=NOW)
        activate DB
        DB-->>SVC: Zaktualizowano poświadczenia i unieważniono token (atomowo)
        deactivate DB

        SVC-->>CTL: Hasło pomyślnie zmienione
        CTL-->>UI: 200 OK
        UI-->>M: Wyświetlenie potwierdzenia i przekierowanie do logowania
    else [Token niepoprawny, wygasły lub już wykorzystany]
        SVC-->>CTL: 400 Bad Request (Nieprawidłowy lub wygasły token)
        CTL-->>UI: 400 Bad Request
        UI-->>M: Komunikat błędu: Link wygasł, wygeneruj nowe żądanie
    end
    deactivate SVC
    deactivate CTL
    deactivate UI
```

---

### 10. Podsumowanie Pokrycia Dynamiki

Zaprojektowane diagramy sekwencji pokrywają **kluczowe** zachowania dynamiczne systemu PKampus (ścieżki krytyczne MVP), a nie pełną listę wszystkich FR:
1. **Asynchroniczność i integracja e-mail:** Zastosowanie kolejki zadań asynchronicznych w Spring Boot dla Mailpit (aktywacja konta, usterki, awarie sprzętu, reset hasła).
2. **Bezpieczeństwo transakcyjne:** Blokady bazodanowe i ograniczenia `EXCLUDE USING gist` wykluczające nakładające się rezerwacje w pralniach i salkach.
3. **Automatyzacja procesów w tle:** Dedykowany Spring Scheduler realizujący regułę 15 minut (§2 ust. 5 Regulaminu).
4. **Zarządzanie mediami:** Bezpośrednia integracja backendu z magazynem obiektowym MinIO (S3) przy obsłudze usterek i zdjęć profilowych.
5. **Egzekwowanie prawa wewnętrznego PK:** Walidacja czarnej listy kar dyscyplinarnych (§6 ust. 2) przed dopuszczeniem do zasobów.
6. **Kaskadowa reakcja na awarie zasobów:** Automatyczne wyłączenie sprzętu, anulowanie rezerwacji, dyspozycja naprawy i powiadomienia mieszkańców.
7. **Bezpieczne zarządzanie tożsamością (IdM):** Dwuetapowa weryfikacja meldunku przez ADS oraz jednorazowe kryptograficzne tokeny resetu hasła (TTL 15 min).
