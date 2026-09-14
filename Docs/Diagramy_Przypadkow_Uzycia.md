# Specyfikacja Diagramów Przypadków Użycia (UML Use Case) - System PKampus

---

## 1. Aktorzy Systemu

1. **Mieszkaniec (Student)** – zameldowany student korzystający z funkcji bytowych, rezerwacji, karty i tablicy.
2. **Recepcjonista (Portier)** – pracownik portierni (weryfikacja wejść, wydawanie/odbiór kluczy, koordynacja napraw, komunikaty dyżurne).
3. **Administrator Domu Studenckiego (ADS / DORM_ADMIN)** – Kierownik DS / pracownik Administracji Domu Studenckiego (weryfikacja meldunków, konfiguracja zasobów, nakładanie sankcji regulaminowych, oficjalne komunikaty DS).
4. **Superadmin (AOS / SUPER_ADMIN)** – Kierownik OS / pracownik Administracji Osiedla Studenckiego (zarządzanie obiektami domów studenckich, publikacja oficjalnych komunikatów ogólnokampusowych, globalna moderacja tablicy kampusu).
5. **System (Scheduler)** – automatyczny proces uwalniający nieodebrane rezerwacje po 15 minutach.

---

## 2. Diagram Ogólny Architektury Przypadków Użycia (High-Level)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef studentStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef staffStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef moduleStyle fill:#edf2f7,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    Student["Mieszkaniec<br/>(Student)"]:::studentStyle

    subgraph Granica_Systemu ["SYSTEM PKAMPUS - PODSYSTEMY"]
        direction TB
        M1["Moduł 1: AUTH & CARD<br/>(Karta Mieszkańca)"]:::moduleStyle
        M2["Moduł 2: LAUNDRY & ROOMS<br/>(Pralnie i Salki)"]:::moduleStyle
        M3["Moduł 3: ISSUES<br/>(Obsługa Usterek)"]:::moduleStyle
        M4["Moduł 4: BOARD & EVENTS<br/>(Życie Kampusu i Tablica)"]:::moduleStyle
        M5["Moduł 5: ADMIN<br/>(Konfiguracja i Kary)"]:::moduleStyle
    end

    Portier["Recepcjonista<br/>(Portier)"]:::staffStyle
    AdminDS["Administrator DS<br/>(ADS)"]:::staffStyle
    SuperAdmin["Superadmin<br/>(AOS)"]:::staffStyle

    %% Relacje Mieszkańca (lewa strona)
    Student --- M1
    Student --- M2
    Student --- M3
    Student --- M4

    %% Relacje Portiera (prawa strona - operacje codzienne i dyżurne)
    M1 --- Portier
    M2 --- Portier
    M3 --- Portier
    M4 --- Portier

    %% Relacje Administracji (prawa strona - nadzór i konfiguracja)
    M1 --- AdminDS
    M1 --- SuperAdmin
    M2 --- AdminDS
    M3 --- AdminDS
    M4 --- AdminDS
    M4 --- SuperAdmin
    M5 --- AdminDS
    M5 --- SuperAdmin
```

### 2.1. Macierz Asocjacji: Aktorzy a Podsystemy

| Aktor | M1: AUTH & CARD | M2: LAUNDRY & ROOMS | M3: ISSUES | M4: BOARD & EVENTS | M5: ADMIN |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Mieszkaniec (Student)** | Dostęp (Karta, profil) | Dostęp (Rezerwacje) | Dostęp (Zgłoszenia) | Dostęp (Tablica, kalendarz) | Brak dostępu |
| **Recepcjonista (Portier)** | Dostęp (Weryfikacja karty) | Dostęp (Klucze, blokada awaryjna) | Rejestr napraw i zgłaszanie awarii | Dostęp (Komunikaty dyżurne, pościel) | Brak dostępu |
| **Administrator DS (ADS)** | Weryfikacja meldunku | Wyłączenie awaryjne (Nadzór) | Zgłoszenia części wspólnych i auto-awaria (FR-LAUND-06) | Komunikaty i moderacja | Konfiguracja i kary |
| **Superadmin (AOS)** | Zarządzanie kontami ADS (`DORM_ADMIN`) | Brak operacji | Brak operacji | Komunikaty kampusowe i moderacja CAMPUS | Zarządzanie osiedlem i kontami ADS |

---

## 3. Pakiet 1: Uwierzytelnianie i Wirtualna Karta Mieszkańca (AUTH & CARD)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef actorStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef ucStyle fill:#fff,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    Student["Mieszkaniec"]:::actorStyle

    subgraph System_Auth ["SYSTEM PKAMPUS: AUTH & CARD"]
        direction TB
        UC_AUTH_01(["UC-AUTH-01: Rejestracja konta"]):::ucStyle
        UC_AUTH_02(["UC-AUTH-02: Logowanie do systemu"]):::ucStyle
        UC_AUTH_03(["UC-AUTH-03: Aktywacja meldunku"]):::ucStyle
        UC_AUTH_04(["UC-AUTH-04: Edycja profilu i hasła"]):::ucStyle
        UC_AUTH_05(["UC-AUTH-05: Reset hasła (link e-mail)"]):::ucStyle
        UC_CARD_01(["UC-CARD-01: Wyświetlenie karty mieszkańca"]):::ucStyle
        UC_CARD_02(["UC-CARD-02: Weryfikacja wizualna karty"]):::ucStyle
    end

    AdminDS["Administrator DS"]:::actorStyle
    Portier["Recepcjonista"]:::actorStyle

    Student --- UC_AUTH_01
    Student --- UC_AUTH_02
    Student --- UC_AUTH_04
    Student --- UC_AUTH_05
    Student --- UC_CARD_01

    AdminDS --- UC_AUTH_03
    Portier --- UC_CARD_02
```

---

## 4. Pakiet 2A: Rezerwacja Pralni (LAUNDRY)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef actorStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef ucStyle fill:#fff,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    Student["Mieszkaniec"]:::actorStyle

    subgraph System_Laundry ["SYSTEM PKAMPUS: REZERWACJA PRALNI"]
        direction TB
        UC_LAUND_01(["UC-LAUND-01: Przeglądanie grafiku pralek"]):::ucStyle
        UC_LAUND_02(["UC-LAUND-02: Rezerwacja slotu pralki"]):::ucStyle
        UC_LAUND_03(["UC-LAUND-03: Anulowanie rezerwacji pralki"]):::ucStyle
        UC_LAUND_04(["UC-LAUND-04: Wydanie i zwrot klucza pralni"]):::ucStyle
        UC_LAUND_05(["UC-LAUND-05: Ręczne zwolnienie slotu (Portier 15 min)"]):::ucStyle
        UC_LAUND_06(["UC-LAUND-06: Awaryjne wyłączenie pralki"]):::ucStyle
        UC_SYS_01(["UC-SYS-01: Automatyczne zwolnienie slotu (Scheduler 15 min)"]):::ucStyle
    end

    Portier["Recepcjonista"]:::actorStyle
    AdminDS["Administrator DS"]:::actorStyle
    Sys["System (Scheduler)"]:::actorStyle

    Student --- UC_LAUND_01
    Student --- UC_LAUND_02
    Student --- UC_LAUND_03

    Portier --- UC_LAUND_04
    Portier --- UC_LAUND_05
    Portier --- UC_LAUND_06
    AdminDS --- UC_LAUND_06
    Sys --- UC_SYS_01
```

---

## 5. Pakiet 2B: Rezerwacja Salek Tematycznych (ROOMS)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef actorStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef ucStyle fill:#fff,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    Student["Mieszkaniec"]:::actorStyle

    subgraph System_Rooms ["SYSTEM PKAMPUS: SALKI TEMATYCZNE"]
        direction TB
        UC_ROOM_01(["UC-ROOM-01: Rezerwacja salki tematycznej"]):::ucStyle
        UC_ROOM_02(["UC-ROOM-02: Anulowanie rezerwacji salki"]):::ucStyle
        UC_ROOM_03(["UC-ROOM-03: Przedłużenie rezerwacji salki (COULD)"]):::ucStyle
        UC_ROOM_04(["UC-ROOM-04: Wydanie i zwrot klucza salki"]):::ucStyle
        UC_ROOM_05(["UC-ROOM-05: Ręczne zwolnienie slotu (Portier 15 min)"]):::ucStyle
        UC_ROOM_06(["UC-ROOM-06: Wyłączenie salki z eksploatacji (Remont/Awaria)"]):::ucStyle
        UC_ADM_03(["UC-ADM-03: Blokada salek (kara 1-3 mies.)"]):::ucStyle
        UC_SYS_02(["UC-SYS-02: Automatyczne zwolnienie slotu (Scheduler 15 min)"]):::ucStyle
    end

    Portier["Recepcjonista"]:::actorStyle
    AdminDS["Administrator DS"]:::actorStyle
    Sys["System (Scheduler)"]:::actorStyle

    Student --- UC_ROOM_01
    Student --- UC_ROOM_02
    Student --- UC_ROOM_03

    Portier --- UC_ROOM_04
    Portier --- UC_ROOM_05
    Portier --- UC_ROOM_06
    AdminDS --- UC_ROOM_06
    AdminDS --- UC_ADM_03
    Sys --- UC_SYS_02
```

---

## 6. Pakiet 3: Ewidencja i Obsługa Usterek (ISSUES)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef studentStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef staffStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef ucStyle fill:#edf2f7,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    Student["Mieszkaniec<br/>(Student)"]:::studentStyle

    subgraph System_Issues ["SYSTEM PKAMPUS: OBSŁUGA USTEREK"]
        direction TB
        subgraph Strefa_Zgloszen ["Zgłaszanie Usterek (Mieszkaniec / Personel)"]
            UC_ISSUE_01(["UC-ISSUE-01<br/>Zgłoszenie usterki / auto-awaria"]):::ucStyle
            UC_ISSUE_05(["UC-ISSUE-05<br/>Podgląd własnych spraw"]):::ucStyle
        end

        subgraph Strefa_Warsztatu ["Portiernia i Warsztat Konserwatora"]
            UC_ISSUE_02(["UC-ISSUE-02<br/>Rejestr awarii w DS"]):::ucStyle
            UC_ISSUE_03(["UC-ISSUE-03<br/>Zamknięcie / status naprawy"]):::ucStyle
            UC_ISSUE_04(["UC-ISSUE-04<br/>Lista dla konserwatora"]):::ucStyle
        end
    end

    Portier["Recepcjonista<br/>(Portier)"]:::staffStyle
    AdminDS["Administrator DS<br/>(ADS)"]:::staffStyle

    %% Relacje mieszkańca po lewej stronie
    Student --- UC_ISSUE_01
    Student --- UC_ISSUE_05

    %% Relacje personelu po prawej stronie (warsztat oraz zgłaszanie awarii części wspólnych / auto-zgłoszenie)
    UC_ISSUE_01 --- Portier
    UC_ISSUE_01 --- AdminDS
    UC_ISSUE_02 --- Portier
    UC_ISSUE_03 --- Portier
    UC_ISSUE_04 --- Portier
```

---

## 7. Pakiet 4A: Tablica Ogłoszeń Sąsiedzkich (BOARD)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef actorStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef ucStyle fill:#fff,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    Student["Mieszkaniec"]:::actorStyle

    subgraph System_Board ["SYSTEM PKAMPUS: TABLICA SĄSIEDZKA"]
        direction TB
        UC_BOARD_01(["UC-BOARD-01: Dodanie ogłoszenia sąsiedzkiego"]):::ucStyle
        UC_BOARD_02(["UC-BOARD-02: Dodanie komentarza w wątku"]):::ucStyle
        UC_BOARD_03(["UC-BOARD-03: Oznaczenie ogłoszenia jako rozwiązane"]):::ucStyle
        UC_BOARD_04(["UC-BOARD-04: Moderacja i usuwanie wpisów"]):::ucStyle
    end

    AdminDS["Administrator DS"]:::actorStyle
    SuperAdmin["Superadmin (AOS)"]:::actorStyle

    Student --- UC_BOARD_01
    Student --- UC_BOARD_02
    Student --- UC_BOARD_03

    AdminDS --- UC_BOARD_04
    SuperAdmin --- UC_BOARD_04
```

---

## 8. Pakiet 4B: Kalendarz i Komunikaty Dyżurne (EVENTS)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef actorStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef ucStyle fill:#fff,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    Student["Mieszkaniec"]:::actorStyle

    subgraph System_Events ["SYSTEM PKAMPUS: KALENDARZ I KOMUNIKATY"]
        direction TB
        UC_EVT_01(["UC-EVT-01: Przeglądanie kalendarza i alertów"]):::ucStyle
        UC_EVT_02(["UC-EVT-02: Utworzenie wydarzenia integracyjnego"]):::ucStyle
        UC_EVT_03(["UC-EVT-03: Publikacja komunikatu / terminu pościeli"]):::ucStyle
    end

    Portier["Recepcjonista"]:::actorStyle
    AdminDS["Administrator DS"]:::actorStyle
    SuperAdmin["Superadmin (AOS)"]:::actorStyle

    Student --- UC_EVT_01
    Student --- UC_EVT_02

    Portier --- UC_EVT_03
    AdminDS --- UC_EVT_03
    SuperAdmin --- UC_EVT_03
```

---

## 9. Pakiet 5: Administracja Zasobami i Nadzór (ADMIN)

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart LR
    classDef actorStyle fill:#2d3748,stroke:#4a5568,stroke-width:2px,color:#fff;
    classDef ucStyle fill:#fff,stroke:#2b6cb0,stroke-width:2px,color:#1a202c;

    AdminDS["Administrator DS<br/>(ADS)"]:::actorStyle

    subgraph System_Admin ["SYSTEM PKAMPUS: ADMINISTRACJA I NADZÓR"]
        direction TB
        UC_ADM_01(["UC-ADM-01: Konfiguracja pralek i slotów per DS"]):::ucStyle
        UC_ADM_02(["UC-ADM-02: Konfiguracja salek tematycznych"]):::ucStyle
        UC_ADM_03(["UC-ADM-03: Nałożenie kary blokady salek (1-3 mies.)"]):::ucStyle
        UC_ADM_04(["UC-ADM-04: Zarządzanie kontami portierów"]):::ucStyle
        UC_ADM_05(["UC-ADM-05: Zarządzanie obiektami akademików"]):::ucStyle
        UC_ADM_06(["UC-ADM-06: Zarządzanie kontami Administratorów DS"]):::ucStyle
    end

    SuperAdmin["Superadmin<br/>(AOS)"]:::actorStyle

    AdminDS --- UC_ADM_01
    AdminDS --- UC_ADM_02
    AdminDS --- UC_ADM_03
    AdminDS --- UC_ADM_04

    SuperAdmin --- UC_ADM_05
    SuperAdmin --- UC_ADM_06
```

---

## 10. Macierz Śledzenia Wymagań (Traceability Matrix)

| Wymaganie FR | Identyfikator Use Case | Nazwa przypadku użycia | Główny Aktor |
| :--- | :--- | :--- | :--- |
| **FR-AUTH-01, FR-AUTH-02** | **UC-AUTH-01, UC-AUTH-03** | Rejestracja konta i aktywacja meldunku | Mieszkaniec, Admin DS |
| **FR-AUTH-03, FR-AUTH-04** | **UC-AUTH-02** | Logowanie i autoryzacja JWT (RBAC) | Wszyscy |
| **FR-AUTH-05** | **UC-ADM-04** | Zarządzanie kontami personelu portierni | Admin DS |
| **FR-AUTH-06** | **UC-AUTH-04** | Edycja profilu i zmiana hasła | Mieszkaniec |
| **FR-AUTH-07** | **UC-AUTH-05** | Procedura resetowania hasła przez link e-mail | Mieszkaniec, System |
| **FR-AUTH-08** | **UC-ADM-06** | Zarządzanie kontami Administratorów DS (AOS) | Superadmin (AOS) |
| **FR-CARD-01 .. FR-CARD-04** | **UC-CARD-01, UC-CARD-02** | Karta mieszkańca i weryfikacja wzrokowa (anty-screenshot) | Mieszkaniec, Portier |
| **FR-LAUND-01** | **UC-ADM-01** | Konfiguracja pralek i parametrów slotów per DS | Admin DS |
| **FR-LAUND-02 .. FR-LAUND-04** | **UC-LAUND-01, UC-LAUND-02, UC-LAUND-03** | Harmonogram, rezerwacja pralki i anulowanie slotu | Mieszkaniec |
| **FR-LAUND-05** | **UC-LAUND-04, UC-LAUND-05, UC-SYS-01** | Odbiór i zwrot klucza pralni, ręczne i automatyczne zwalnianie slotu po 15 min | Portier, System (Scheduler) |
| **FR-LAUND-06** | **UC-LAUND-06** | Awaryjne wyłączenie pralki, auto-anulowanie slotów i zgłoszenie naprawy | Portier, Admin DS |
| **FR-ROOM-01, FR-ROOM-02** | **UC-ADM-02** | Katalog i parametryzacja salek per DS (Kujon, Chillout) | Admin DS |
| **FR-ROOM-03, FR-ROOM-04** | **UC-ROOM-01** | Rezerwacja salki (formularz organizatora i regulamin) | Mieszkaniec |
| **FR-ROOM-05** | **UC-ROOM-04, UC-ROOM-05, UC-SYS-02** | Odbiór i zwrot klucza salki, ręczne i automatyczne zwalnianie slotu po 15 min | Portier, System (Scheduler) |
| **FR-ROOM-06** | **UC-ROOM-03** | Wniosek o przedłużenie rezerwacji salki (COULD) | Mieszkaniec |
| **FR-ROOM-07** | **UC-ADM-03** | Ewidencja kar i czarna lista salek (blokada 1–3 mies.) | Admin DS |
| **FR-ROOM-08** | **UC-ROOM-02** | Anulowanie rezerwacji salki przed startem (SHOULD) | Mieszkaniec |
| **FR-ROOM-09** | **UC-ROOM-06** | Wyłączenie salki z eksploatacji (Remont / Awaria) | Portier, Admin DS |
| **FR-ISSUE-01 .. FR-ISSUE-05** | **UC-ISSUE-01 .. UC-ISSUE-03, UC-ISSUE-05** | Zgłoszenia awarii ze zdjęciem MinIO, rejestr i obsługa | Mieszkaniec, Portier, Admin DS |
| **FR-ISSUE-06** | **UC-ISSUE-04** | Generowanie listy zadań dla konserwatora | Portier |
| **FR-BOARD-01 .. FR-BOARD-05** | **UC-BOARD-01 .. UC-BOARD-03** | Tablica ogłoszeń (SHOULD / Etap 4) | Mieszkaniec |
| **FR-BOARD-02** | **UC-BOARD-01** (widok feedu) | Filtrowanie feedu (SHOULD; bez full-text search) | Mieszkaniec |
| **FR-BOARD-06** | **UC-BOARD-04** | Moderacja i usuwanie wpisów na tablicy (SHOULD) | Admin DS, Superadmin (AOS) |
| **FR-EVENT-01, FR-EVENT-02** | **UC-EVT-03, UC-EVT-01** | Publikacja komunikatów + baner przypiętych CRITICAL | Portier, Admin DS, Superadmin (AOS), Mieszkaniec |
| **FR-EVENT-03** | **UC-EVT-01** | Kalendarz oficjalnych terminów kampusu | Mieszkaniec |
| **FR-EVENT-04** | **UC-EVT-02** | Wydarzenia mieszkańców (COULD) | Mieszkaniec |
| **FR-PORTAL-01 .. FR-PORTAL-04** | **UC-LAUND-04, UC-ROOM-04, UC-ADM-01 .. UC-ADM-04** | Dashboard recepcji, klucze, konfiguracja i meldunki | Portier, Admin DS |
| **FR-PORTAL-05** | **UC-ADM-05, UC-ADM-06, UC-EVT-03, UC-BOARD-04** | Zarządzanie obiektami akademików, kontami ADS, komunikacja ogólnokampusowa i moderacja | Superadmin (AOS) |
