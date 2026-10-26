#!/usr/bin/env bash
# Executed over SSH by campus.yml. Paths are relative to the login user's home.
set -euo pipefail
umask 077

RELEASE_ID="${1:?Release ID is required}"
[[ "$RELEASE_ID" =~ ^[0-9]+-[0-9]+$ ]] || { echo 'Invalid release ID'; exit 1; }
PROJECT="$HOME/campus-pickup"
WORK="$HOME/campus-deploy"
STAGE="$WORK/incoming/$RELEASE_ID"
BACKUP="$WORK/backups/$RELEASE_ID"

for command in rsync flock python3 tar; do command -v "$command" >/dev/null; done
test -f "$PROJECT/.env.server"
test -f "$PROJECT/compose.server.yml"
sudo -n docker compose version >/dev/null

mkdir -p "$WORK/incoming" "$BACKUP"
exec 9>"$WORK/deploy.lock"
flock -w 1200 9
mkdir -p "$STAGE"
tar -xzf "$WORK/incoming/$RELEASE_ID.tgz" -C "$STAGE"
test -s "$STAGE/target/dianping-0.0.1-SNAPSHOT.jar"

compose() {
  sudo -n docker compose -p campus-server --env-file "$PROJECT/.env.server" \
    -f "$PROJECT/compose.server.yml" "$@"
}

tar -czf "$BACKUP/project.tar.gz" -C "$HOME" campus-pickup
compose exec -T mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --no-tablespaces --routines --triggers --events "$MYSQL_DATABASE"' \
  > "$BACKUP/database.sql" < /dev/null
test -s "$BACKUP/database.sql"

sudo -n docker compose -p campus-server --env-file "$PROJECT/.env.server" \
  -f "$STAGE/compose.server.yml" config --quiet
sudo -n docker build --target runtime -t "campus-pickup:release-$RELEASE_ID" "$STAGE"

# Preserve server-only files and environment secrets.
rsync -a --exclude='.env' --exclude='.env.*' --exclude='.git' "$STAGE/" "$PROJECT/"
sudo -n docker tag campus-pickup:server campus-pickup:previous
sudo -n docker tag "campus-pickup:release-$RELEASE_ID" campus-pickup:server
compose up -d --no-deps --no-build app
compose up -d --no-deps --no-build --force-recreate gateway

# Probe inside gateway, independent of the host's HTTP_BIND/HTTP_PORT.
for attempt in $(seq 1 60); do
  if compose exec -T gateway wget -q -T 5 -O - http://127.0.0.1/actuator/health/readiness < /dev/null |
    python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin).get("status") == "UP" else 1)'
  then
    echo "Deployment successful: $RELEASE_ID; backup: $BACKUP"
    exit 0
  fi
  sleep 3
done

echo "Readiness check failed. Previous image: campus-pickup:previous; backup: $BACKUP"
compose ps
exit 1
