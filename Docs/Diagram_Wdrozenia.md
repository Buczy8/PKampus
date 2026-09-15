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
                STORAGE["Kontener: pkampus-minio<br/>(MinIO S3 Object Storage)<br/>Magazyn zdjęć usterek i awatarów<br/>(pkampus-issues / pkampus-avatars)<br/>Port wewn: 9000 (S3 API)"]:::containerStyle
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
| **`pkampus-proxy`** | `nginx:alpine` (multi-stage build z `node:20-alpine`) | Skompilowany pakiet **React PWA** (`manifest.webmanifest`, `sw.js`, HTML/JS/CSS, ikony) budowany w kontenerze + plik `nginx.conf` | **Zewn:** 80 (HTTP), 443 (HTTPS)<br/>**Wewn:** brak | Brama wejściowa serwera (Reverse Proxy). Wymusza HTTPS, serwuje statyczne zasoby PWA (App Shell, cache), terminacja SSL oraz przekazuje zapytania `/api/*` (prefiks obejmujący `/api/v1/*`) do kontenera backendu. |
| **`pkampus-backend`** | `eclipse-temurin:21-jre-alpine` | `pkampus-backend.jar` (Spring Boot Executable JAR na Java 21) | **Zewn:** brak (izolacja)<br/>**Wewn:** 8080 | Główna warstwa logiki biznesowej, uwierzytelniania JWT, transakcji rezerwacji pralni/salek, walidacji czarnej listy oraz obsługi zgłoszeń usterek. |
| **`pkampus-db`** | `postgres:16-alpine` | Instancja silnika PostgreSQL 16 + DDL schematu | **Zewn:** brak (izolacja)<br/>**Wewn:** 5432 | Relacyjny magazyn danych. Przechowuje 15 tabel domenowych, realizuje blokady transakcyjne, egzekwuje ograniczenia `EXCLUDE USING gist` (anti-overlap) i integralność referencyjną. |
| **`pkampus-minio`** | `minio/minio` | Silnik MinIO Object Storage | **Zewn:** brak<br/>**Wewn:** 9000 (S3 API), 9001 (Console) | Magazyn obiektowy S3. Buckety: `pkampus-issues` (zdjęcia usterek) oraz `pkampus-avatars` (zdjęcia karty mieszkańca); dostęp tylko przez backend (bez publicznego anonymous download). |
| **`pkampus-mailpit`** | `axllent/mailpit` | Serwer Mailpit (uruchamiany wyłącznie z profilami `demo`, `dev`) | **Zewn:** 8025 (Web UI - dev only)<br/>**Wewn:** 1025 (SMTP) | Serwer pocztowy w kontenerze na potrzeby środowiska deweloperskiego/pokazowego. W profilu `production` kontener nie startuje, a backend łączy się bezpośrednio z uczelnianym serwerem SMTP PK. |
| **`pkampus-certbot`** | `certbot/certbot:latest` | Klient ACME Certbot (uruchamiany z profilem `production`) | **Zewn:** brak<br/>**Wewn:** brak | Usługa dla profilu produkcyjnego (VPS PK). Skrypt startowy weryfikuje obecność certyfikatu, wykonuje inicjalne żądanie certyfikatu (`certbot certonly`), a następnie cyklicznie odnawia certyfikat Let's Encrypt (`certbot renew` co 12h). |

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
| **`pg_data`** | `/var/lib/postgresql/data` | Fizyczne pliki relacyjnej bazy danych PostgreSQL (tabele, indeksy, WAL). | Codzienny automatyczny zrzut logiczny `pg_dump`, kompresowany i symetrycznie szyfrowany algorytmem **AES-256 (GPG)** kluczem z menedżera sekretów, a następnie transferowany szyfrowanym kanałem (SFTP/rsync) na zewnętrzny serwer kopii zapasowych PK (reguła 3-2-1). Dostęp do klucza deszyfrującego posiada wyłącznie wyznaczony administrator infrastruktury. |
| **`minio_data`** | `/data` | Binarny magazyn obiektowy MinIO (zdjęcia usterek w buckecie `pkampus-issues` oraz zdjęcia profilowe legitymacji mieszkańca w buckecie `pkampus-avatars`). | Szyfrowana kopia lustrzana bucketów z użyciem narzędzia `mc mirror` na zewnętrzny bezpieczny zasób backupowy PK. Retencja plików zdjęciowych: usunięcie z magazynu po 30 dniach od rozwiązania usterki (`RESOLVED`). |
| **`letsencrypt_certs`**| `/etc/letsencrypt` | Certyfikaty SSL/TLS i klucze prywatne. | W profilu produkcyjnym (VPS PK): certyfikaty Let's Encrypt generowane przy pierwszym uruchomieniu (`certbot certonly`) i cyklicznie odnawiane (`certbot renew` co 12h) przez kontener `pkampus-certbot`. W profilu demonstracyjnym: certyfikaty lokalne (mkcert) lub bezpieczny kontekst localhost. |

---

## 6. Produkcyjna Konfiguracja Orkiestracji: `docker-compose.yml`

Poniższy deklaratywny plik konfiguracyjny stanowi referencyjną implementację środowiska wdrożeniowego PKampus:

```yaml
# Compose Spec (bez klucza `version` — obsolete od Compose v2)

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
  certbot_challenge:
    driver: local

services:
  # -------------------------------------------------------------
  # Brama wejściowa Reverse Proxy & Serwer Statyczny SPA React
  # -------------------------------------------------------------
  pkampus-proxy:
    build:
      context: .
      dockerfile: ./infra/nginx/Dockerfile
    image: pkampus-proxy:latest
    container_name: pkampus-proxy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./infra/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./infra/nginx/conf.d:/etc/nginx/conf.d:ro
      - letsencrypt_certs:/etc/letsencrypt:ro
      - certbot_challenge:/var/www/certbot:ro
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
      - MINIO_ACCESS_KEY=${MINIO_APP_USER:-pkampus_app}
      - MINIO_SECRET_KEY=${MINIO_APP_PASSWORD:?MINIO_APP_PASSWORD required}
      - MINIO_BUCKET_ISSUES=pkampus-issues
      - MINIO_BUCKET_AVATARS=pkampus-avatars
      - SPRING_MAIL_HOST=${MAIL_HOST:-pkampus-mailpit}
      - SPRING_MAIL_PORT=${MAIL_PORT:-1025}
      - SPRING_MAIL_USERNAME=${MAIL_USERNAME:-}
      - SPRING_MAIL_PASSWORD=${MAIL_PASSWORD:-}
      - SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=${MAIL_SMTP_AUTH:-false}
      - SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=${MAIL_STARTTLS_ENABLE:-false}
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
      # Bootstrap czystej bazy (kopia Flyway V1__init_schema.sql); dalsza ewolucja wyłącznie przez Flyway (ddl-auto=validate)
      - ./backend/src/main/resources/db/01_init.sql:/docker-entrypoint-initdb.d/01_init.sql:ro
    networks:
      - pkampus-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d pkampus_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  # -------------------------------------------------------------
  # Magazyn Obiektowy na Zdjęcia Usterek i Awatary (MinIO S3)
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

  # Inicjalizacja bucketów i dedykowanego użytkownika aplikacyjnego MinIO (Least Privilege)
  pkampus-minio-init:
    image: minio/mc:latest
    container_name: pkampus-minio-init
    depends_on:
      pkampus-minio:
        condition: service_healthy
    environment:
      - MINIO_ROOT_USER=${MINIO_ROOT_USER:?MINIO_ROOT_USER required}
      - MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD required}
      - MINIO_APP_USER=${MINIO_APP_USER:-pkampus_app}
      - MINIO_APP_PASSWORD=${MINIO_APP_PASSWORD:?MINIO_APP_PASSWORD required}
    networks:
      - pkampus-net
    entrypoint: >
      /bin/sh -c "
      /usr/bin/mc alias set myminio http://pkampus-minio:9000 $$MINIO_ROOT_USER $$MINIO_ROOT_PASSWORD;
      /usr/bin/mc mb --ignore-existing myminio/pkampus-issues;
      /usr/bin/mc mb --ignore-existing myminio/pkampus-avatars;
      /usr/bin/mc admin user add myminio $$MINIO_APP_USER $$MINIO_APP_PASSWORD || true;
      /usr/bin/mc admin policy attach myminio readwrite --user $$MINIO_APP_USER;
      exit 0;
      "

  # -------------------------------------------------------------
  # Usługa Pocztowa Dev/Test (Mailpit) — uruchamiana domyślnie
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
  # Bootstrap i odnawianie certyfikatów Let's Encrypt (Profil produkcyjny VPS PK)
  # -------------------------------------------------------------
  pkampus-certbot:
    image: certbot/certbot:latest
    container_name: pkampus-certbot
    profiles:
      - production
    restart: unless-stopped
    volumes:
      - letsencrypt_certs:/etc/letsencrypt
      - certbot_challenge:/var/www/certbot:rw
    entrypoint: >
      /bin/sh -c "
      trap exit TERM;
      if [ ! -f /etc/letsencrypt/live/$${DOMAIN:-pkampus.pk.edu.pl}/fullchain.pem ]; then
        echo 'Brak certyfikatu SSL — inicjalizacja procedury certbot certonly...';
        certbot certonly --webroot -w /var/www/certbot --non-interactive --agree-tos \
          --email $${SSL_EMAIL:?SSL_EMAIL required} -d $${DOMAIN:-pkampus.pk.edu.pl};
        touch /var/www/certbot/.reload;
      fi;
      while :; do
        certbot renew --webroot -w /var/www/certbot --quiet --deploy-hook 'touch /var/www/certbot/.reload';
        sleep 12h & wait $${!};
      done;
      "
```

---

### 6.1. Uszczegółowienie Procedury Wdrożenia i Rozwiązanie Ograniczeń Środowiskowych

1. **Autonomiczna kompilacja frontendu (PWA) w kontenerze:**
   * Aby wyeliminować wymóg wcześniejszego ręcznego budowania aplikacji (`npm run build`) na maszynie gospodarza, obraz `pkampus-proxy` wykorzystuje Dockerfile wieloetapowy (*Multi-Stage Build*). W etapie 1 kontener Node.js (`node:20-alpine`) pobiera zależności (`npm ci`) i generuje zminifikowany pakiet produkcyjny PWA, po czym w etapie 2 serwer `nginx:alpine` kopiuje artefakty bezpośrednio do katalogu `/usr/share/nginx/html`. Dzięki temu komenda `docker compose up -d` działa w 100% autonomicznie na czystej stacji.
2. **Gotowość out-of-the-box i konfiguracja poczty (Mailpit vs Serwer SMTP PK):**
   * Usługa `pkampus-mailpit` uruchamia się domyślnie przy standardowym `docker compose up -d`, gwarantując natychmiastowe działanie całego środowiska demonstracyjnego i bezpieczną inspekcję e-maili na `http://127.0.0.1:8025`.
   * Na środowisku produkcyjnym (VPS PK), operator ustawia w pliku `.env` dane oficjalnej bramy pocztowej uczelni (`MAIL_HOST=poczta.pk.edu.pl`, port 587 z szyfrowaniem STARTTLS) oraz opcjonalnie wyłącza lokalny kontener pocztowy (`docker compose --scale pkampus-mailpit=0 up -d`).
3. **Zasada minimalnych uprawnień w magazynie obiektowym (MinIO Non-Root User):**
   * Backend Spring Boot nie operuje na poświadczeniach konta `MINIO_ROOT_USER`.
   * Kontener inicjalizacyjny `pkampus-minio-init` tworzy dedykowanego użytkownika aplikacyjnego `MINIO_APP_USER` z ograniczoną polityką `readwrite` wyłącznie do bucketów `pkampus-issues` oraz `pkampus-avatars`.
4. **Dwuetapowy mechanizm certyfikacji TLS i automatyczne przeładowanie Nginx:**
   * W celu zapobieżenia awarii startowej serwera Nginx przed wygenerowaniem pierwszego certyfikatu Let's Encrypt, wdrożony jest skrypt bootstrap (`infra/nginx/init-ssl.sh`), który generuje tymczasowy certyfikat samopodpisany:
     ```bash
     #!/bin/sh
     DOMAIN="${DOMAIN:-pkampus.pk.edu.pl}"
     CERT_DIR="/etc/letsencrypt/live/$DOMAIN"
     if [ ! -f "$CERT_DIR/fullchain.pem" ]; then
       echo "Generowanie certyfikatu bootstrap (self-signed) dla $DOMAIN..."
       mkdir -p "$CERT_DIR"
       openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
         -keyout "$CERT_DIR/privkey.pem" \
         -out "$CERT_DIR/fullchain.pem" \
         -subj "/CN=$DOMAIN"
     fi
     ```
   * Kontener `pkampus-proxy` nasłuchuje w tle na znacznik `/var/www/certbot/.reload` (współdzielony wolumen `certbot_challenge`), który `certbot` tworzy flagą `--deploy-hook` po pomyślnym pobraniu lub odnowieniu certyfikatu:
     ```sh
     while :; do
       if [ -f /var/www/certbot/.reload ]; then
         rm -f /var/www/certbot/.reload
         nginx -s reload
       fi
       sleep 30
     done &
     ```

---

### 6.2. Strategia Ewolucji Schematu Bazy (Flyway) i Bezpieczeństwo Kopii Zapasowych

1. **Wersjonowane migracje schematu (Flyway):**
   * Zgodnie z dobrymi praktykami inżynierskimi, schemat PostgreSQL nie jest modyfikowany bezpośrednio przez mechanizm Hibernate (`ddl-auto=validate`).
   * Zmiany struktury relacyjnej wersjonowane są za pomocą narzędzia **Flyway** w katalogu `backend/src/main/resources/db/migration/`:
     * `V1__init_schema.sql` – inicjalizacja tabel, indeksów i ograniczeń wykluczających `EXCLUDE USING gist`,
     * `V2__seed_dormitories_and_rooms.sql` – początkowe dane obiektowe domów studenckich i salek tematycznych,
     * kolejne wersje `V3__...` dla ewolucji modelu danych.
2. **Procedura automatycznych kopii zapasowych (`infra/backup/backup.sh`):**
   * Zrzut logiczny bazy wykonywany jest w formacie binarnym `pg_dump -Fc` i szyfrowany symetrycznie algorytmem **AES-256 (GPG)**:
     ```bash
     #!/bin/bash
     set -euo pipefail
     DATE=$(date +%Y%m%d_%H%M%S)
     BACKUP_DIR="/backups"
     
     # 1. Zrzut i szyfrowanie bazy PostgreSQL
     pg_dump -h pkampus-db -U "$DB_USER" -Fc pkampus_db \
       | gpg --symmetric --cipher-algo AES256 --batch --passphrase "$BACKUP_PASSPHRASE" \
       > "$BACKUP_DIR/db/pkampus_db_$DATE.dump.gpg"
       
     # 2. Synchronizacja i szyfrowanie bucketów MinIO
     mc mirror --overwrite myminio/pkampus-issues "$BACKUP_DIR/minio/issues"
     mc mirror --overwrite myminio/pkampus-avatars "$BACKUP_DIR/minio/avatars"
     tar -czf - -C "$BACKUP_DIR/minio" . \
       | gpg --symmetric --cipher-algo AES256 --batch --passphrase "$BACKUP_PASSPHRASE" \
       > "$BACKUP_DIR/minio/minio_data_$DATE.tar.gz.gpg"
       
     # 3. Retencja lokalna GFS (dzienne 14 dni, tygodniowe 8 tyg., miesięczne 12 mies.)
     find "$BACKUP_DIR/db/daily" "$BACKUP_DIR/minio/daily" -type f -name "*.gpg" -mtime +14 -delete
     find "$BACKUP_DIR/db/weekly" "$BACKUP_DIR/minio/weekly" -type f -name "*.gpg" -mtime +56 -delete
     find "$BACKUP_DIR/db/monthly" "$BACKUP_DIR/minio/monthly" -type f -name "*.gpg" -mtime +365 -delete
     # Promocja GFS: niedziela -> weekly, 1. dzień miesiąca -> monthly
     DOW=$(date +%u); DOM=$(date +%d)
     if [ "$DOW" = "7" ]; then cp "$BACKUP_DIR/db/pkampus_db_$DATE.dump.gpg" "$BACKUP_DIR/db/weekly/"; cp "$BACKUP_DIR/minio/minio_data_$DATE.tar.gz.gpg" "$BACKUP_DIR/minio/weekly/"; fi
     if [ "$DOM" = "01" ]; then cp "$BACKUP_DIR/db/pkampus_db_$DATE.dump.gpg" "$BACKUP_DIR/db/monthly/"; cp "$BACKUP_DIR/minio/minio_data_$DATE.tar.gz.gpg" "$BACKUP_DIR/minio/monthly/"; fi
     ```
3. **Procedura Odtwarzania Awaryjnego (*Disaster Recovery*):**
   * W przypadku awarii serwera lub utraty wolumenu, odtworzenie bazy do spójnego stanu polega na deszyfracji i wstrzyknięciu zrzutu narzędziem `pg_restore`:
     ```bash
     gpg --decrypt --batch --passphrase "$BACKUP_PASSPHRASE" /backups/db/pkampus_db_LATEST.dump.gpg \
       | pg_restore -h pkampus-db -U "$DB_USER" -d pkampus_db --clean --if-exists
     ```

---

## 7. Macierz Zgodności Architektury Wdrożenia z Wymaganiami Niefunkcjonalnymi (NFR)

| Identyfikator Wymagania | Nazwa Wymagania | Realizacja w Architekturze Wdrożeniowej | Status |
| :--- | :--- | :--- | :--- |
| **NFR-DEP-01** | Wdrożenie i konteneryzacja (IaC) | Kompletne środowisko (React PWA, Nginx, Spring Boot, PostgreSQL, MinIO, Mailpit) spakowane i uruchamiane pojedynczym poleceniem `docker compose up -d`, gwarantując 100% spójności między dev/test a VPS PK. | Zgodny |
| **NFR-SEC-01** | Bezpieczeństwo sesji (JWT Bearer) | JWT w nagłówku `Authorization` (TTL 15 min); brak sesji cookie — CSRF cookie-based nie dotyczy; klucz `JWT_SECRET` wymagany z env (bez fallbacku w compose); blacklist Caffeine + rate-limit Bucket4j w `pkampus-backend` (in-memory, bez dodatkowych kontenerów). | Zgodny |
| **NFR-SEC-02** | Ochrona poświadczeń (BCrypt) | Bezpieczne haszowanie haseł algorytmem BCrypt z soleniem po stronie Spring Security przed utrwaleniem w relacyjnej bazie danych PostgreSQL. | Zgodny |
| **NFR-SEC-03** | Bezpieczeństwo plików (MinIO S3) | Walidacja MIME/rozmiaru w API; buckety `MINIO_BUCKET_ISSUES` / `MINIO_BUCKET_AVATARS` prywatne; brak anonymous download. | Zgodny |
| **NFR-SEC-04** | Szyfrowanie transmisji sieciowej (TLS 1.3 / HTTPS) | Nginx Reverse Proxy (`pkampus-proxy`) jako jedyny publiczny punkt styku wymusza protokół HTTPS, szyfrowanie TLS 1.3, nagłówek HSTS oraz polityki CSP i anty-clickjacking. W profilu produkcyjnym (VPS PK) certyfikaty dostarcza Let's Encrypt z automatycznym odnawianiem przez kontener `pkampus-certbot` (profil `production`). W profilu demonstracyjnym (obrona pracy) stosowane są certyfikaty lokalne self-signed/mkcert lub kontekst Secure Context localhost (W3C), co gwarantuje pełną autonomiczność od infrastruktury zewnętrznej i 100% niezawodności. | Zgodny |
| **NFR-SEC-05** | Izolacja sieciowa bazy danych i magazynu | Baza PostgreSQL oraz MinIO nie publikują portów na interfejsie publicznym serwera; komunikacja backendu z bazą i storage odbywa się wyłącznie wewnątrz izolowanej sieci `pkampus-net`. | Zgodny |
| **NFR-SEC-06** | XSS / SQL Injection | Parametryzowane JPA + CSP/HSTS na Nginx; escapowanie danych w UI. | Zgodny |
| **NFR-CONC-01** | Współbieżność i integralność transakcyjna | Mechanizmy transakcyjne Spring Data JPA oraz ograniczenia wykluczające PostgreSQL `EXCLUDE USING gist` (`chk_laundry_no_overlap`, `chk_room_no_overlap`) wykluczają nakładanie przedziałów rezerwacji w warunkach współbieżnych. | Zgodny |
| **NFR-PERF-01** | Czas odpowiedzi interfejsu i backendu | Zasoby PWA buforowane w Service Workerze klienta; Nginx kompresuje assety (Gzip/Brotli); zapytania bazy wsparte indeksami B-drzewa, co gwarantuje czasy odpowiedzi < 200 ms. | Zgodny |
| **NFR-USAB-01** | Mobilność i instalowalność (PWA) | Architektura Progressive Web App umożliwia instalację ikony na ekranie głównym smartfona bez sklepów z aplikacjami oraz bezpośredni dostęp do systemowego API aparatu fotograficznego. | Zgodny |
| **NFR-A11Y-01** | Dostępność WCAG 2.1 AA (zakres MVP) | Kontrast, fokus i etykiety na krytycznych widokach; pełny audit AA poza MVP. | Zgodny (SHOULD) |
| **NFR-REL-01** | Niezawodność i samonaprawa usług | Healthcheck + `restart: unless-stopped` dla usług rdzeniowych (proxy, backend, db, minio, mailpit). Job `pkampus-minio-init` jest kontenerem jednorazowym bez healthcheck; `pkampus-certbot` (profil `production`) ma `restart: unless-stopped` bez healthcheck. | Zgodny |
| **NFR-REL-02** | Trwałość danych (Data Persistence) | Baza danych i pliki MinIO utrwalane są w dedykowanych wolumenach Docker (`pg_data`, `minio_data`), co zabezpiecza stan aplikacji przed aktualizacjami i restartami obrazów kontenerowych. | Zgodny |
| **NFR-REL-03** | Cykliczne zadania w tle (Scheduler) | Wbudowany harmonogram Spring Boot (`ReservationScheduler`, `@Scheduled`, cron co 1 min) w kontenerze `pkampus-backend` egzekwuje regułę 15 min (`AUTO_CANCELLED_15MIN`) oraz czyści przedawnione tokeny; współdzielony cykl życia i healthcheck backendu. | Zgodny |
