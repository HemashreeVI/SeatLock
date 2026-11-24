#!/usr/bin/env bash
# Dumps the SeatLock PostgreSQL database, compresses it and streams it to S3.
#
# Runs on the EC2 host (not inside a container). Credentials come from the instance's
# IAM role (see iam-backup-policy.json): no access keys are stored on the server.
#
# Usage:   BACKUP_BUCKET=my-bucket ./backup-to-s3.sh
# Cron:    30 20 * * * BACKUP_BUCKET=my-bucket /home/ubuntu/backup-to-s3.sh >> /home/ubuntu/backup.log 2>&1
set -euo pipefail
export PATH=/usr/local/bin:/usr/bin:/bin:/snap/bin   # cron runs with a minimal PATH

BUCKET="${BACKUP_BUCKET:?set BACKUP_BUCKET to the S3 bucket name}"
CONTAINER="${PG_CONTAINER:-seatlock-postgres}"
FILE="postgres/seatlock-$(date -u +%Y-%m-%d_%H%M).sql.gz"

docker exec "$CONTAINER" pg_dump -U seatlock -d seatlock | gzip | aws s3 cp - "s3://${BUCKET}/${FILE}"
echo "$(date -u) backup uploaded: s3://${BUCKET}/${FILE}"
