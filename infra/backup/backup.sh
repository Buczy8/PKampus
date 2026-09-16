#!/bin/bash
set -euo pipefail

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_DIR:-/backups}"

mkdir -p "$BACKUP_DIR/db/daily" "$BACKUP_DIR/db/weekly" "$BACKUP_DIR/db/monthly"
mkdir -p "$BACKUP_DIR/minio/daily" "$BACKUP_DIR/minio/weekly" "$BACKUP_DIR/minio/monthly"

# 1. Zrzut i szyfrowanie bazy PostgreSQL
echo "Tworzenie kopii zapasowej bazy danych pkampus_db..."
pg_dump -h pkampus-db -U "${DB_USER:-pkampus_user}" -Fc "${POSTGRES_DB:-pkampus_db}" \
  | gpg --symmetric --cipher-algo AES256 --batch --yes --passphrase "${BACKUP_PASSPHRASE}" \
  > "$BACKUP_DIR/db/daily/pkampus_db_$DATE.dump.gpg"

# 2. Synchronizacja i szyfrowanie bucketów MinIO
echo "Tworzenie kopii zapasowej magazynu MinIO S3..."
mc mirror --overwrite myminio/pkampus-issues "$BACKUP_DIR/minio/issues" || true
mc mirror --overwrite myminio/pkampus-avatars "$BACKUP_DIR/minio/avatars" || true
tar -czf - -C "$BACKUP_DIR/minio" issues avatars 2>/dev/null \
  | gpg --symmetric --cipher-algo AES256 --batch --yes --passphrase "${BACKUP_PASSPHRASE}" \
  > "$BACKUP_DIR/minio/daily/minio_data_$DATE.tar.gz.gpg"

# 3. Retencja lokalna GFS (dzienne 14 dni, tygodniowe 8 tyg., miesięczne 12 mies.)
find "$BACKUP_DIR/db/daily" "$BACKUP_DIR/minio/daily" -type f -name "*.gpg" -mtime +14 -delete
find "$BACKUP_DIR/db/weekly" "$BACKUP_DIR/minio/weekly" -type f -name "*.gpg" -mtime +56 -delete
find "$BACKUP_DIR/db/monthly" "$BACKUP_DIR/minio/monthly" -type f -name "*.gpg" -mtime +365 -delete

# Promocja GFS: niedziela (7) -> weekly, 1. dzień miesiąca (01) -> monthly
DOW=$(date +%u)
DOM=$(date +%d)
if [ "$DOW" = "7" ]; then
  cp "$BACKUP_DIR/db/daily/pkampus_db_$DATE.dump.gpg" "$BACKUP_DIR/db/weekly/"
  cp "$BACKUP_DIR/minio/daily/minio_data_$DATE.tar.gz.gpg" "$BACKUP_DIR/minio/weekly/"
fi

if [ "$DOM" = "01" ]; then
  cp "$BACKUP_DIR/db/daily/pkampus_db_$DATE.dump.gpg" "$BACKUP_DIR/db/monthly/"
  cp "$BACKUP_DIR/minio/daily/minio_data_$DATE.tar.gz.gpg" "$BACKUP_DIR/minio/monthly/"
fi

echo "Procedura backupu zakonczona pomyslnie dla sygnatury $DATE."
