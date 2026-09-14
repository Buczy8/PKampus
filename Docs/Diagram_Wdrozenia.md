# Diagram Wdrożenia Systemu PKampus (UML Deployment Diagram)

---

## 1. Wprowadzenie i Cel Diagramu Wdrożenia

Diagram wdrożenia w notacji języka UML (ang. *UML Deployment Diagram*) stanowi kluczowy element projektu technicznego systemu informatycznego. Jego celem jest:
1. **Odwzorowanie fizycznej i wirtualnej topologii środowiska uruchomieniowego** – zdefiniowanie węzłów sprzętowych (komputery użytkowników, serwer uczelniany/VPS) oraz węzłów logicznych (kontenery Docker Engine).
2. **Prezentacja artefaktów oprogramowania (ang. *Execution Artifacts*)** – rozmieszczenie skompilowanych modułów aplikacji (SPA React, archiwum JAR Spring Boot, silnik PostgreSQL, magazyn MinIO, serwer Nginx) w dedykowanych kontenerach.
3. **Formalizacja protokołów i ścieżek komunikacji sieciowej** – określenie wykorzystywanych portów, protokołów (HTTPS, HTTP, JDBC, S3 API, SMTP) oraz mechanizmów bezpieczeństwa (izolacja sieciowa, szyfrowanie TLS 1.3).
4. **Zapewnienie powtarzalności środowiska (ang. *Infrastructure as Code - IaC*)** – precyzyjne powiązanie modelu architektonicznego z deklaratywną konfiguracją orkiestracji `docker-compose.yml`.

---

## 2. Diagram Wdrożenia Środowiska Konteneryzowanego (Docker Compose)


> **Konwencja graficzna:** Diagram zachowuje ścisłą dyscyplinę inżynierską – wszystkie linie połączeń prowadzone są ortogonalnie pod kątem prostym 90° (`curve: stepBefore`), bez przecinania obrysów węzłów oraz bez użycia elementów dekoracyjnych (emotikonów).

```mermaid
%%{init: {'flowchart': {'curve': 'stepBefore'}}}%%
flowchart TD
    classDef nodeStyle fill:#f8fafc,stroke:#334155,stroke-width:2px,color:#0f172a;
    classDef containerStyle fill:#ffffff,stroke:#0284c7,stroke-width:2px,color:#0f172a;
    classDef storageStyle fill:#f1f5f9,stroke:#64748b,stroke-width:1px,stroke-dasharray: 4 4,color:#334155;

    subgraph CLIENT_NODE["Węzeł: Urządzenie Użytkownika (Smartfon / Komputer)"]
        BROWSER["Przeglądarka / Środowisko PWA<br/>(Silnik Chromium, WebKit, Gecko)<br/>Progressive Web App (React)"]:::containerStyle
    end

    subgraph SERVER_NODE["Węzeł: Serwer Uczelniany / VPS PK lub Stacja Demonstracyjna (System operacyjny Linux / macOS / Windows)"]
        subgraph DOCKER_COMPOSE["Środowisko konteneryzacji: Docker Engine & Docker Compose"]
            subgraph BRIDGE_NET["Wirtualna Sieć Mostkowa: pkampus-net (Izolacja 172.28.0.0/16)"]
                NGINX["Kontener: pkampus-proxy<br/>(Nginx Reverse Proxy)<br/>Serwowanie PWA & Brama API<br/>Porty zewn: 80 (HTTP), 443 (HTTPS)"]:::containerStyle
                BACKEND["Kontener: pkampus-backend<br/>(Java / Spring Boot REST API)<br/>Warstwa logiki biznesowej<br/>Port wewn: 8080 (nieeksponowany)"]:::containerStyle
                DATABASE["Kontener: pkampus-db<br/>(PostgreSQL)<br/>Relacyjna baza danych<br/>Port wewn: 5432 (nieeksponowany)"]:::containerStyle
                STORAGE["Kontener: pkampus-minio<br/>(MinIO S3 Object Storage)<br/>Magazyn zdjęć usterek<br/>Port wewn: 9000 (S3 API)"]:::containerStyle
                MAILPIT["Kontener: pkampus-mailpit<br/>(Mailpit SMTP)<br/>Lokalny serwer pocztowy<br/>Port wewn: 1025 (SMTP)"]:::containerStyle
            end

            subgraph VOLUMES["Trwałe Wolumeny Danych (Docker Named Volumes)"]
                VOL_DB[("pg_data<br/>/var/lib/postgresql/data")]:::storageStyle
                VOL_MINIO[("minio_data<br/>/data")]:::storageStyle
                VOL_CERTS[("letsencrypt_certs<br/>/etc/letsencrypt")]:::storageStyle
            end
        end
    end

    BROWSER -->|HTTPS : 443 / TLS 1.3| NGINX
    NGINX -->|HTTP : 8080 / Proxy Pass /api| BACKEND
    BACKEND -->|JDBC : 5432 / PostgreSQL Driver| DATABASE
    BACKEND -->|HTTP : 9000 / AWS S3 SDK v2| STORAGE
    BACKEND -->|SMTP : 1025 / Local SMTP| MAILPIT

    DATABASE --- VOL_DB
    STORAGE --- VOL_MINIO
    NGINX --- VOL_CERTS
```

---

## 3. Specyfikacja Węzłów i Artefaktów Wykonawczych

Zgodnie z wymogami inżynierii oprogramowania każdy węzeł i artefakt środowiska wykonawczego posiada precyzyjną charakterystykę techniczną:

### 3.1. Tabela Węzłów Sprzętowych i Wirtualnych (Nodes)

| Identyfikator Węzła | Typ Węzła | Środowisko bazowe (OS / Runtime) | Adresacja / Rola |
| :--- | :--- | :--- | :--- |
| **Urządzenie Użytkownika (Smartfon / Komputer)** | Fizyczny (Smartfon Android/iOS, laptop, stacja robocza) | Dowolny system operacyjny (Android, iOS, Windows, Linux, macOS) | Klient HTTP uruchamiający przeglądarkę WWW lub zainstalowaną aplikację **PWA (Progressive Web Application)** z obsługą manifestu instalacyjnego, Service Workera i aparatu fotograficznego (zgłaszanie usterek). |
| **Serwer Uczelniany / VPS PK (lub Stacja Demonstracyjna)** | Maszyna wirtualna / serwer dedykowany / stacja robocza (laptop) | Linux Server (Ubuntu/Debian) lub środowisko lokalne (Linux/macOS/Windows z Docker Desktop) | Główny węzeł aplikacyjny hostujący silnik Docker Engine oraz orkiestrator Docker Compose. Obsługuje dwa profile: **profil demonstracyjny** (lokalny, w 100% autonomiczny, certyfikaty lokalne/localhost, brak zależności od sieci PK — gwarancja niezawodności obrony) oraz **profil produkcyjny** (VPS PK, subdomena uczelniana, TLS 1.3 Let's Encrypt z automatycznym odnawianiem przez kontener certbot). |

---

### 3.2. Tabela Kontenerów i Artefaktów Oprogramowania (Execution Artifacts)

| Nazwa Kontenera | Obraz bazowy (Docker Image) | Artefakt oprogramowania | Porty wewn. / zewn. | Rola i odpowiedzialność w systemie |
| :--- | :--- | :--- | :--- | :--- |
| **`pkampus-proxy`** | `nginx:alpine` | Skompilowany pakiet **React PWA** (`manifest.webmanifest`, `sw.js`, HTML/JS/CSS, ikony) + plik `nginx.conf` | **Zewn:** 80 (HTTP), 443 (HTTPS)<br/>**Wewn:** brak | Brama wejściowa serwera (Reverse Proxy). Wymusza HTTPS, serwuje statyczne zasoby PWA (App Shell, cache), terminacja SSL oraz przekazuje zapytania `/api/*` do kontenera backendu. |
| **`pkampus-backend`** | `eclipse-temurin:21-jre-alpine` | `pkampus-backend.jar` (Spring Boot Executable JAR na Java 21) | **Zewn:** brak (izolacja)<br/>**Wewn:** 8080 | Główna warstwa logiki biznesowej, uwierzytelniania JWT, transakcji rezerwacji pralni/salek, walidacji czarnej listy oraz obsługi zgłoszeń usterek. |
| **`pkampus-db`** | `postgres:16-alpine` | Instancja silnika PostgreSQL 16 + DDL schematu | **Zewn:** brak (izolacja)<br/>**Wewn:** 5432 | Relacyjny magazyn danych. Przechowuje 15 tabel domenowych, realizuje blokady transakcyjne, egzekwuje ograniczenia `EXCLUDE USING gist` (anti-overlap) i integralność referencyjną. |
| **`pkampus-minio`** | `minio/minio` | Silnik MinIO Object Storage | **Zewn:** brak<br/>**Wewn:** 9000 (S3 API), 9001 (Console) | Magazyn obiektowy S3. Buckety: `pkampus-issues` (zdjęcia usterek) oraz `pkampus-avatars` (zdjęcia karty mieszkańca); dostęp tylko przez backend (bez publicznego anonymous download). |
| **`pkampus-mailpit`** | `axllent/mailpit` | Serwer Mailpit | **Zewn:** 8025 (Web UI - dev only)<br/>**Wewn:** 1025 (SMTP) | Serwer pocztowy w kontenerze. Przechwytuje wiadomości e-mail z linkami aktywacyjnymi meldunku i powiadomieniami o rezerwacjach bez ryzyka wysyłki spamu. |
| **`pkampus-certbot`** | `certbot/certbot:latest` | Klient ACME Certbot (uruchamiany z profilem `production`) | **Zewn:** brak<br/>**Wewn:** brak | Usługa opcjonalna dla profilu produkcyjnego (VPS PK). Odpowiada za cykliczne, automatyczne odnawianie certyfikatów SSL/TLS Let's Encrypt w wolumenie `letsencrypt_certs` poprzez wyzwanie HTTP-01 webroot Nginx. W profilu demonstracyjnym nie jest uruchamiana. |

---

## 4. Architektura Sieciowa i Bezpieczeństwo

### 4.1. Segmentacja Sieci i Zasada Minimalnych Uprawnień (Least Privilege)
Architektura wdrożeniowa realizuje koncepcję **głębokiej obrony (ang. *Defense in Depth*)**:
1. **Pojedynczy publiczny punkt wejścia**: Jedynym kontenerem posiadającym zmapowane porty publiczne na interfejsie sieciowym serwera (`0.0.0.0`) jest `pkampus-proxy` (porty 80 i 443).
2. **Izolacja baz danych i storage'u**: Kontenery `pkampus-db`, `pkampus-backend`, `pkampus-minio` oraz `pkampus-mailpit` operują wyłącznie wewnątrz wirtualnej sieci mostkowej Docker (`pkampus-net`, podsieć `172.28.0.0/16`). Żaden podmiot z publicznego internetu nie ma możliwości bezpośredniego nawiązania połączenia z portem bazy danych (5432) ani S3 API (9000).
3. **Translacja nazw DNS w sieci Docker**: Kontenery komunikują się ze sobą za pośrednictwem wbudowanego resolvera DNS Dockera, posługując się unikalnymi nazwami usług (np. `jdbc:postgresql://pkampus-db:5432/pkampus_db`).

### 4.2. Protokoły Komunikacyjne i Szyfrowanie

```
[Klient / Smartfon / PWA]
        │
        │  HTTPS (TLS 1.3, AES-256-GCM, Port 443)
        ▼
[pkampus-proxy (Nginx)]
   │                │
   │ (PWA Assets)   │  HTTP (REST API JSON, Port 8080)
   ▼                ▼
[App Shell / SW] [pkampus-backend (Spring Boot)]
                    │
                    ├── JDBC / TCP (Port 5432) ──────────► [pkampus-db (PostgreSQL)]
                    │
                    ├── S3 API / HTTP (Port 9000) ───────► [pkampus-minio (S3 Storage)]
                    │
                    └── SMTP / TCP (Port 1025) ──────────► [pkampus-mailpit (Mailpit)]
```

### 4.3. Polityka Bezpieczeństwa HTTP na poziomie Nginx
Kontener Reverse Proxy wyposażony jest w zestaw nagłówków zabezpieczających:
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (wymuszenie HTTPS / HSTS),
- `X-Frame-Options: SAMEORIGIN` (ochrona przed atakami Clickjacking),
- `X-Content-Type-Options: nosniff` (blokada MIME-sniffing),
- `Content-Security-Policy: default-src 'self'; img-src 'self' data:; connect-src 'self' /api;` (ochrona przed wstrzykiwaniem skryptów XSS).

---

## 5. Architektura Pamięci Masowej i Wolumeny Trwałe (Storage & Volumes)

Zgodnie z zasadą bezstanowości kontenerów aplikacyjnych (ang. *Stateless Containers*), wszystkie dane wymagające trwałego przechowywania (ang. *Persistence*) są odseparowane od cyklu życia kontenerów i mapowane do dedykowanych wolumenów Docker (ang. *Named Volumes*):

| Nazwa Wolumenu | Ścieżka docelowa w kontenerze | Typ danych | Polityka kopii zapasowych (Backup Policy) |
| :--- | :--- | :--- | :--- |
| **`pg_data`** | `/var/lib/postgresql/data` | Fizyczne pliki relacyjnej bazy danych PostgreSQL (tabele, indeksy, WAL). | Codzienny automatyczny zrzut logiczny poleceniem `pg_dump` do archiwum skompresowanego `*.sql.gz` na wydzieloną przestrzeń backupową. |
| **`minio_data`** | `/data` | Binarny magazyn obiektowy MinIO (zdjęcia usterek w buckecie `pkampus-issues` oraz zdjęcia profilowe legitymacji mieszkańca w buckecie `pkampus-avatars`). | Kopia lustrzana bucketów z użyciem narzędzia `mc mirror` na zewnętrzny serwer plików PK. |
| **`letsencrypt_certs`**| `/etc/letsencrypt` | Certyfikaty SSL/TLS i klucze prywatne. | W profilu produkcyjnym (VPS PK): certyfikaty Let's Encrypt generowane i cyklicznie odnawiane przez kontener `pkampus-certbot`. W profilu demonstracyjnym: certyfikaty lokalne (self-signed/mkcert) lub bezpieczny kontekst localhost. |

---

## 6. Produkcyjna Konfiguracja Orkiestracji: `docker-compose.yml`

Poniższy deklaratywny plik konfiguracyjny stanowi referencyjną implementację środowiska wdrożeniowego PKampus:

```yaml
version: '3.8'

networks:
  pkampus-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/16

volumes:
  pg_data:
    driver: local
  minio_data:
    driver: local
  letsencrypt_certs:
    driver: local

services:
  # -------------------------------------------------------------
  # Brama wejściowa Reverse Proxy & Serwer Statyczny SPA React
  # -------------------------------------------------------------
  pkampus-proxy:
    image: nginx:alpine
    container_name: pkampus-proxy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./infra/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./infra/nginx/conf.d:/etc/nginx/conf.d:ro
      - ./frontend/dist:/usr/share/nginx/html:ro
      - letsencrypt_certs:/etc/letsencrypt:ro
      - ./infra/nginx/certbot-challenge:/var/www/certbot:ro
    networks:
      - pkampus-net
    depends_on:
      pkampus-backend:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:80/ || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 3
      start_period: 10s

  # -------------------------------------------------------------
  # Warstwa Aplikacyjna i Logika Biznesowa (Spring Boot)
  # -------------------------------------------------------------
  pkampus-backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    image: pkampus-backend:latest
    container_name: pkampus-backend
    restart: unless-stopped
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://pkampus-db:5432/pkampus_db
      - SPRING_DATASOURCE_USERNAME=${DB_USER:?DB_USER required}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD:?DB_PASSWORD required}
      - SPRING_JPA_HIBERNATE_DDL_AUTO=validate
      - MINIO_ENDPOINT=http://pkampus-minio:9000
      - MINIO_ACCESS_KEY=${MINIO_ROOT_USER:?MINIO_ROOT_USER required}
      - MINIO_SECRET_KEY=${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD required}
      - MINIO_BUCKET_ISSUES=pkampus-issues
      - MINIO_BUCKET_AVATARS=pkampus-avatars
      - SPRING_MAIL_HOST=${MAIL_HOST:-pkampus-mailpit}
      - SPRING_MAIL_PORT=${MAIL_PORT:-1025}
      - JWT_SECRET=${JWT_SECRET:?JWT_SECRET required}
    networks:
      - pkampus-net
    depends_on:
      pkampus-db:
        condition: service_healthy
      pkampus-minio:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep UP || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 25s

  # -------------------------------------------------------------
  # Relacyjna Baza Danych (PostgreSQL)
  # -------------------------------------------------------------
  pkampus-db:
    image: postgres:16-alpine
    container_name: pkampus-db
    restart: unless-stopped
    environment:
      - POSTGRES_DB=pkampus_db
      - POSTGRES_USER=${DB_USER:?DB_USER required}
      - POSTGRES_PASSWORD=${DB_PASSWORD:?DB_PASSWORD required}
    volumes:
      - pg_data:/var/lib/postgresql/data
      - ./backend/src/main/resources/db/01_init.sql:/docker-entrypoint-initdb.d/01_init.sql:ro
    networks:
      - pkampus-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d pkampus_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  # -------------------------------------------------------------
  # Magazyn Obiektowy na Zdjęcia Usterek (MinIO S3)
  # -------------------------------------------------------------
  pkampus-minio:
    image: minio/minio:latest
    container_name: pkampus-minio
    restart: unless-stopped
    command: server /data --console-address ":9001"
    environment:
      - MINIO_ROOT_USER=${MINIO_ROOT_USER:?MINIO_ROOT_USER required}
      - MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD required}
    volumes:
      - minio_data:/data
    networks:
      - pkampus-net
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9000/minio/health/live || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 3

  # Inicjalizacja bucketów MinIO S3 (issues oraz avatars) — job jednorazowy
  pkampus-minio-init:
    image: minio/mc:latest
    container_name: pkampus-minio-init
    depends_on:
      pkampus-minio:
        condition: service_healthy
    environment:
      - MINIO_ROOT_USER=${MINIO_ROOT_USER:?MINIO_ROOT_USER required}
      - MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD required}
    networks:
      - pkampus-net
    entrypoint: >
      /bin/sh -c "
      /usr/bin/mc alias set myminio http://pkampus-minio:9000 $$MINIO_ROOT_USER $$MINIO_ROOT_PASSWORD;
      /usr/bin/mc mb --ignore-existing myminio/pkampus-issues;
      /usr/bin/mc mb --ignore-existing myminio/pkampus-avatars;
      exit 0;
      "

  # -------------------------------------------------------------
  # Usługa Pocztowa Dev/Test (Mailpit)
  # -------------------------------------------------------------
  pkampus-mailpit:
    image: axllent/mailpit
    container_name: pkampus-mailpit
    restart: unless-stopped
    ports:
      - "127.0.0.1:8025:8025" # Dostęp do panelu WWW wyłącznie z localhost / tunelu SSH
    networks:
      - pkampus-net
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://127.0.0.1:8025/ || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 3

  # -------------------------------------------------------------
  # Automatyczne odnawianie certyfikatów Let's Encrypt (Profil produkcyjny VPS PK)
  # -------------------------------------------------------------
  pkampus-certbot:
    image: certbot/certbot:latest
    container_name: pkampus-certbot
    profiles:
      - production
    restart: unless-stopped
    volumes:
      - letsencrypt_certs:/etc/letsencrypt
      - ./infra/nginx/certbot-challenge:/var/www/certbot:rw
    entrypoint: >
      /bin/sh -c "
      trap exit TERM;
      while :; do
        certbot renew --webroot -w /var/www/certbot --quiet;
        sleep 12h & wait $${!};
      done;
      "
```

---

## 7. Macierz Zgodności Architektury Wdrożenia z Wymaganiami Niefunkcjonalnymi (NFR)

| Identyfikator Wymagania | Nazwa Wymagania | Realizacja w Architekturze Wdrożeniowej | Status |
| :--- | :--- | :--- | :--- |
| **NFR-DEP-01** | Wdrożenie i konteneryzacja (IaC) | Kompletne środowisko (React PWA, Nginx, Spring Boot, PostgreSQL, MinIO, Mailpit) spakowane i uruchamiane pojedynczym poleceniem `docker compose up -d`, gwarantując 100% spójności między dev/test a VPS PK. | Zgodny |
| **NFR-SEC-01** | Bezpieczeństwo sesji (JWT Bearer) | JWT w nagłówku `Authorization`; brak sesji cookie — CSRF cookie-based nie dotyczy; klucz `JWT_SECRET` wymagany z env (bez fallbacku w compose). | Zgodny |
| **NFR-SEC-02** | Ochrona poświadczeń (BCrypt) | Bezpieczne haszowanie haseł algorytmem BCrypt z soleniem po stronie Spring Security przed utrwaleniem w relacyjnej bazie danych PostgreSQL. | Zgodny |
| **NFR-SEC-03** | Bezpieczeństwo plików (MinIO S3) | Walidacja MIME/rozmiaru w API; buckety `MINIO_BUCKET_ISSUES` / `MINIO_BUCKET_AVATARS` prywatne; brak anonymous download. | Zgodny |
| **NFR-SEC-04** | Szyfrowanie transmisji sieciowej (TLS 1.3 / HTTPS) | Nginx Reverse Proxy (`pkampus-proxy`) jako jedyny publiczny punkt styku wymusza protokół HTTPS, szyfrowanie TLS 1.3, nagłówek HSTS oraz polityki CSP i anty-clickjacking. W profilu produkcyjnym (VPS PK) certyfikaty dostarcza Let's Encrypt z automatycznym odnawianiem przez kontener `pkampus-certbot` (profil `production`). W profilu demonstracyjnym (obrona pracy) stosowane są certyfikaty lokalne self-signed/mkcert lub kontekst Secure Context localhost (W3C), co gwarantuje pełną autonomiczność od infrastruktury zewnętrznej i 100% niezawodności. | Zgodny |
| **NFR-SEC-05** | Izolacja sieciowa bazy danych i magazynu | Baza PostgreSQL oraz MinIO nie publikują portów na interfejsie publicznym serwera; komunikacja backendu z bazą i storage odbywa się wyłącznie wewnątrz izolowanej sieci `pkampus-net`. | Zgodny |
| **NFR-SEC-06** | XSS / SQL Injection | Parametryzowane JPA + CSP/HSTS na Nginx; escapowanie danych w UI. | Zgodny |
| **NFR-CONC-01** | Współbieżność i integralność transakcyjna | Mechanizmy transakcyjne Spring Data JPA oraz ograniczenia wykluczające PostgreSQL `EXCLUDE USING gist` (`chk_laundry_no_overlap`, `chk_room_no_overlap`) wykluczają nakładanie przedziałów rezerwacji w warunkach współbieżnych. | Zgodny |
| **NFR-PERF-01** | Czas odpowiedzi interfejsu i backendu | Zasoby PWA buforowane w Service Workerze klienta; Nginx kompresuje assety (Gzip/Brotli); zapytania bazy wsparte indeksami B-drzewa, co gwarantuje czasy odpowiedzi < 200 ms. | Zgodny |
| **NFR-USAB-01** | Mobilność i instalowalność (PWA) | Architektura Progressive Web App umożliwia instalację ikony na ekranie głównym smartfona bez sklepów z aplikacjami oraz bezpośredni dostęp do systemowego API aparatu fotograficznego. | Zgodny |
| **NFR-A11Y-01** | Dostępność WCAG 2.1 AA (zakres MVP) | Kontrast, fokus i etykiety na krytycznych widokach; pełny audit AA poza MVP. | Zgodny (SHOULD) |
| **NFR-REL-01** | Niezawodność i samonaprawa usług | Healthcheck + `restart: unless-stopped` dla usług rdzeniowych (proxy, backend, db, minio, mailpit). Job `pkampus-minio-init` jest kontenerem jednorazowym bez healthcheck. | Zgodny |
| **NFR-REL-02** | Trwałość danych (Data Persistence) | Baza danych i pliki MinIO utrwalane są w dedykowanych wolumenach Docker (`pg_data`, `minio_data`), co zabezpiecza stan aplikacji przed aktualizacjami i restartami obrazów kontenerowych. | Zgodny |
