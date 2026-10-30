#!/bin/bash
set -euo pipefail

keyfile=/data/configdb/engolearn-keyfile
if [ ! -s "$keyfile" ]; then
    umask 077
    dd if=/dev/urandom bs=756 count=1 status=none | base64 | tr -d '\n' > "${keyfile}.tmp"
    mv "${keyfile}.tmp" "$keyfile"
fi
chown mongodb:mongodb "$keyfile"
chmod 400 "$keyfile"

# Keep the image's existing-user detection, first-run init and privilege drop.
exec /usr/local/bin/docker-entrypoint.sh "$@"
