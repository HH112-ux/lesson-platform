#!/usr/bin/env bash
#
# Backup and restore drill for the local stack: take a MySQL logical backup and a MinIO
# object backup, restore both into isolated targets, and compare the result with the
# source row by row.
#
# Usage:
#   bash scripts/backup-drill.sh
#
# What this drill proves, and what it does not (read this before quoting the numbers):
#   1. The backup is logical: a mysqldump stream and a mirrored object tree, not a
#      physical copy of the data directory. It proves the data can be re-created on a
#      clean server; it says nothing about crash consistency of a filesystem snapshot.
#   2. RPO equals the interval between two dumps. No binlog is shipped and no point in
#      time recovery is rehearsed here, so every write after a dump is lost in a real
#      incident. Continuous protection needs binlog archiving and a separate drill.
#   3. The timings below are measured on the local demo dataset, against a throwaway
#      container on the same host. They are not a production RTO: production restores
#      have far more rows, real disks, network hops and concurrent traffic.
#
# Isolation:
#   MySQL is restored into a throwaway mysql:8.0.40 container (--rm, no volume, random
#   free host port on loopback) so the source database is never a restore target.
#   MinIO is mirrored into a timestamped bucket; the source bucket is only read.
#
# Comparison:
#   MySQL, per database: table list, CHECKSUM TABLE of every base table, the highest
#   flyway_schema_history version, and COUNT(*) per table.
#   MinIO: object count, total bytes and the ETag of every object. For single part
#   uploads the ETag is the MD5 of the body; multipart uploads keep the ETag format
#   "<md5>-<parts>", which still has to match byte for byte after a preserve mirror.
#
# Output: logs/backup-drill-<timestamp>/ with the dump, the manifests and
# restore-report.txt. The script exits non-zero on the first mismatch.
#
# Environment:
#   LP_DRILL_LOG_DIR=...   override the output directory
#
# Requires Docker on the host, same as dev-up.sh. The stack must be running.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSQL_CONTAINER="lp-mysql"
MYSQL_ROOT_PASSWORD="${LP_MYSQL_ROOT_PASSWORD:-lp_root_dev}"
MYSQL_IMAGE="mysql:8.0.40"
MINIO_CONTAINER="lp-minio"
MINIO_USER="${LP_MINIO_ROOT_USER:-lpminio}"
MINIO_PASSWORD="${LP_MINIO_ROOT_PASSWORD:-lp_minio_dev}"
MINIO_BUCKET="${LP_MINIO_BUCKET:-lp-media}"
DATABASES=(lp_ums lp_cms lp_oms lp_lms lp_ugc lp_ai lp_ops)

LOG_DIR="${LP_DRILL_LOG_DIR:-$ROOT/logs/backup-drill-$(date +%Y%m%d-%H%M%S)}"
REPORT="$LOG_DIR/restore-report.txt"
RESTORE_CONTAINER=""
RESTORE_BUCKET="lp-media-drill-restore-$(date +%Y%m%d-%H%M%S)"

mkdir -p "$LOG_DIR" || { printf 'FAIL cannot create %s\n' "$LOG_DIR" >&2; exit 1; }
printf 'backup-drill: output in %s\n' "$LOG_DIR"

FAILURES=0
log() { printf '\n==> %s\n' "$1"; }
fail() { printf 'FAIL %s\n' "$1" >&2; exit 1; }
report() { printf '%s\n' "$1" | tee -a "$REPORT"; }
# Whole seconds are too coarse for this dataset: every phase would report zero.
now_ms() { perl -MTime::HiRes -e 'printf "%d", Time::HiRes::time() * 1000'; }

cleanup() {
  if [ -n "$RESTORE_CONTAINER" ]; then
    docker rm -f "$RESTORE_CONTAINER" >/dev/null 2>&1 || true
  fi
  # A failed run may leave temporary directories and the restore bucket behind.
  # rb, not rm: rm empties a bucket but leaves it in place.
  docker exec "$MINIO_CONTAINER" sh -c '
    mc alias set drill http://127.0.0.1:9000 "$1" "$2" >/dev/null 2>&1
    mc rb --force "drill/$3" >/dev/null 2>&1
  ' sh "$MINIO_USER" "$MINIO_PASSWORD" "$RESTORE_BUCKET" || true
  docker exec "$MINIO_CONTAINER" rm -rf /tmp/lp-drill-backup /tmp/lp-drill-restore \
    >/dev/null 2>&1 || true
}
trap cleanup EXIT

# mc talks to the local server from inside its own container: the minio/mc image is not
# cached on the host and Docker Hub is not reachable from here, while the server image
# already ships the same client.
mc_run() {
  docker exec "$MINIO_CONTAINER" sh -c '
    mc alias set drill http://127.0.0.1:9000 "$1" "$2" >/dev/null && shift 2 && mc "$@"
  ' sh "$MINIO_USER" "$MINIO_PASSWORD" "$@"
}

# Source queries run in the stack container; restore queries run in the throwaway one.
mysql_source() {
  docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" \
    mysql -uroot -N -B --default-character-set=utf8mb4 "$@"
}
mysql_restore() {
  docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$RESTORE_CONTAINER" \
    mysql -uroot -N -B --default-character-set=utf8mb4 "$@"
}

pick_free_port() {
  local port=13306
  while [ "$port" -lt 13406 ]; do
    if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
      printf '%s' "$port"
      return 0
    fi
    port=$((port + 1))
  done
  return 1
}

# diff_check NAME A B compares two manifest files and records the verdict.
diff_check() {
  local name="$1" a="$2" b="$3"
  if diff -q "$a" "$b" >/dev/null 2>&1; then
    report "PASS $name"
  else
    FAILURES=$((FAILURES + 1))
    report "FAIL $name"
    diff -u "$a" "$b" | head -40 >> "$REPORT"
  fi
}

command -v docker >/dev/null 2>&1 || fail "docker is required"
health="$(docker inspect -f '{{.State.Health.Status}}' "$MYSQL_CONTAINER" 2>/dev/null)" ||
  fail "$MYSQL_CONTAINER not found; run scripts/dev-up.sh first"
[ "$health" = "healthy" ] || fail "$MYSQL_CONTAINER is $health, expected healthy"

# ---------------------------------------------------------------- MySQL backup
DUMP="$LOG_DIR/mysql-all.sql.gz"
log "mysqldump ${DATABASES[*]}"
dump_start="$(now_ms)"
if ! docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" \
  mysqldump -uroot \
  --single-transaction --quick --routines --events --triggers \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 \
  --databases "${DATABASES[@]}" | gzip -1 > "$DUMP"; then
  fail "mysqldump failed, see $DUMP"
fi
dump_ms=$(( $(now_ms) - dump_start ))
dump_bytes="$(wc -c < "$DUMP" | tr -d ' ')"
[ "$dump_bytes" -gt 1024 ] || fail "dump is only $dump_bytes bytes"

# ------------------------------------------------------- isolated restore target
log "starting isolated restore container"
PORT="$(pick_free_port)" || fail "no free port between 13306 and 13405"
RESTORE_CONTAINER="lp-backup-drill-$$"
docker run -d --rm --name "$RESTORE_CONTAINER" \
  -p "127.0.0.1:${PORT}:3306" \
  -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  "$MYSQL_IMAGE" >/dev/null || fail "cannot start $RESTORE_CONTAINER"

ready=0
for _ in $(seq 1 60); do
  if docker exec "$RESTORE_CONTAINER" \
    mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD" --silent >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
[ "$ready" = "1" ] || { docker logs "$RESTORE_CONTAINER" 2>&1 | tail -20 >&2; fail "restore container never became ready"; }
report "restore container: $RESTORE_CONTAINER on 127.0.0.1:$PORT (--rm, no volume)"

log "restoring the dump"
restore_start="$(now_ms)"
if ! gunzip -c "$DUMP" |
  docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$RESTORE_CONTAINER" mysql -uroot; then
  fail "restore failed"
fi
restore_ms=$(( $(now_ms) - restore_start ))

# ------------------------------------------------------------ MySQL comparison
log "comparing source and restored databases"
db_list=""
for db in "${DATABASES[@]}"; do
  [ -n "$db_list" ] && db_list="$db_list,"
  db_list="$db_list'$db'"
done

table_sql="SELECT CONCAT(table_schema,'.',table_name) FROM information_schema.tables
WHERE table_schema IN ($db_list) AND table_type='BASE TABLE' ORDER BY 1;"
mysql_source -e "$table_sql" > "$LOG_DIR/tables.source.txt" || fail "table list query failed"
mysql_restore -e "$table_sql" > "$LOG_DIR/tables.restore.txt" || fail "table list query failed on restore"
diff_check "table list ($(wc -l < "$LOG_DIR/tables.source.txt" | tr -d ' ') tables)" \
  "$LOG_DIR/tables.source.txt" "$LOG_DIR/tables.restore.txt"

TABLES="$(tr '\n' ' ' < "$LOG_DIR/tables.source.txt")"
[ -n "$TABLES" ] || fail "source has no tables in ${DATABASES[*]}"

count_sql=""
checksum_sql=""
for table in $TABLES; do
  [ -n "$count_sql" ] && count_sql="$count_sql UNION ALL "
  count_sql="$count_sql SELECT '$table', COUNT(*) FROM $table"
  [ -n "$checksum_sql" ] && checksum_sql="$checksum_sql, "
  checksum_sql="$checksum_sql $table"
done
mysql_source -e "$count_sql" > "$LOG_DIR/rows.source.txt" || fail "row count query failed"
mysql_restore -e "$count_sql" > "$LOG_DIR/rows.restore.txt" || fail "row count query failed on restore"
diff_check "row counts (total $(awk -F'\t' '{s+=$2} END {print s+0}' "$LOG_DIR/rows.source.txt") rows)" \
  "$LOG_DIR/rows.source.txt" "$LOG_DIR/rows.restore.txt"

mysql_source -e "CHECKSUM TABLE $checksum_sql;" > "$LOG_DIR/checksum.source.txt" ||
  fail "checksum query failed"
mysql_restore -e "CHECKSUM TABLE $checksum_sql;" > "$LOG_DIR/checksum.restore.txt" ||
  fail "checksum query failed on restore"
diff_check "checksum table ($(grep -c . "$LOG_DIR/checksum.source.txt") tables)" \
  "$LOG_DIR/checksum.source.txt" "$LOG_DIR/checksum.restore.txt"

flyway_sql=""
for db in "${DATABASES[@]}"; do
  [ -n "$flyway_sql" ] && flyway_sql="$flyway_sql UNION ALL "
  flyway_sql="$flyway_sql SELECT '$db', IFNULL(MAX(CAST(version AS UNSIGNED)),0) FROM $db.flyway_schema_history"
done
mysql_source -e "$flyway_sql" > "$LOG_DIR/flyway.source.txt" || fail "flyway query failed"
mysql_restore -e "$flyway_sql" > "$LOG_DIR/flyway.restore.txt" || fail "flyway query failed on restore"
diff_check "flyway max version" "$LOG_DIR/flyway.source.txt" "$LOG_DIR/flyway.restore.txt"

# ---------------------------------------------------------------- MinIO backup
log "mirroring MinIO bucket $MINIO_BUCKET"
mkdir -p "$LOG_DIR/minio"
# The manifest is read before the mirror: an empty bucket must fail loudly instead of
# producing a green drill that proved nothing.
mc_run ls --recursive --json "drill/$MINIO_BUCKET" \
  > "$LOG_DIR/minio/source.jsonl" 2> "$LOG_DIR/minio/mc-source.err" ||
  fail "cannot list bucket $MINIO_BUCKET"
jq -r 'select(.type == "file") | "\(.key)\t\(.size)\t\(.etag)"' \
  "$LOG_DIR/minio/source.jsonl" | sort > "$LOG_DIR/objects.source.txt"
[ -s "$LOG_DIR/objects.source.txt" ] || fail "bucket $MINIO_BUCKET is empty, nothing to prove"

mirror_start="$(now_ms)"
# Direction 1: bucket -> backup tree. The tree is then copied out to the host, so the
# backup does not live only inside the MinIO container.
mc_run mirror --preserve "drill/$MINIO_BUCKET" /tmp/lp-drill-backup ||
  fail "mirror from the bucket to the backup tree failed"
docker cp "$MINIO_CONTAINER:/tmp/lp-drill-backup" "$LOG_DIR/minio/backup" ||
  fail "cannot copy the backup tree to the host"
# Direction 2: backup tree -> a new bucket, which is what a real restore does.
docker cp "$LOG_DIR/minio/backup" "$MINIO_CONTAINER:/tmp/lp-drill-restore" ||
  fail "cannot copy the backup tree back into the container"
mc_run mb -p "drill/$RESTORE_BUCKET" >/dev/null || fail "cannot create $RESTORE_BUCKET"
mc_run mirror --preserve /tmp/lp-drill-restore "drill/$RESTORE_BUCKET" ||
  fail "mirror from the backup tree to the restore bucket failed"
mirror_ms=$(( $(now_ms) - mirror_start ))

mc_run ls --recursive --json "drill/$RESTORE_BUCKET" \
  > "$LOG_DIR/minio/restore.jsonl" 2> "$LOG_DIR/minio/mc-restore.err" ||
  fail "cannot list $RESTORE_BUCKET"
jq -r 'select(.type == "file") | "\(.key)\t\(.size)\t\(.etag)"' \
  "$LOG_DIR/minio/restore.jsonl" | sort > "$LOG_DIR/objects.restore.txt"
diff_check "minio objects ($(wc -l < "$LOG_DIR/objects.source.txt" | tr -d ' ') objects, $(awk -F'\t' '{s+=$2} END {print s+0}' "$LOG_DIR/objects.source.txt") bytes)" \
  "$LOG_DIR/objects.source.txt" "$LOG_DIR/objects.restore.txt"

backup_bytes="$(awk -F'\t' '{s+=$2} END {print s+0}' "$LOG_DIR/objects.source.txt")"

# ---------------------------------------------------------------------- report
{
  printf '\n===== backup drill report =====\n'
  printf 'date                : %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')"
  printf 'mysql dump          : %s bytes (%s KiB), gzip -1\n' \
    "$dump_bytes" "$((dump_bytes / 1024))"
  printf 'backup elapsed      : %s ms (mysqldump)\n' "$dump_ms"
  printf 'restore elapsed     : %s ms (gunzip + mysql import)\n' "$restore_ms"
  printf 'tables compared     : %s\n' "$(grep -c . "$LOG_DIR/tables.source.txt")"
  printf 'rows compared       : %s\n' "$(awk -F'\t' '{s+=$2} END {print s+0}' "$LOG_DIR/rows.source.txt")"
  printf 'flyway max versions : %s\n' "$(tr '\n' ' ' < "$LOG_DIR/flyway.source.txt")"
  printf 'minio objects       : %s (%s bytes mirrored, %s ms)\n' \
    "$(wc -l < "$LOG_DIR/objects.source.txt" | tr -d ' ')" "$backup_bytes" "$mirror_ms"
  if [ "$FAILURES" -eq 0 ]; then
    printf 'result              : ALL CHECKS PASSED\n'
  else
    printf 'result              : %s CHECK(S) FAILED, see the diffs above\n' "$FAILURES"
  fi
  printf 'scope               : logical backup, RPO = dump interval (no binlog PITR), '
  printf 'timings are local demo data and are not a production RTO\n'
} | tee -a "$REPORT"

[ "$FAILURES" -eq 0 ] || { printf '\nBACKUP DRILL FAILED\n' >&2; exit 1; }
printf '\nBACKUP DRILL OK: dump, restore and MinIO mirror match the source.\n'
