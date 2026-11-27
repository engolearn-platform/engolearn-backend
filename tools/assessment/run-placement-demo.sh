#!/usr/bin/env bash
set -euo pipefail
umask 077

fail() { printf '%s\n' "$*" >&2; exit 1; }

[[ $# -eq 1 ]] || fail "Cách chạy: bash tools/assessment/run-placement-demo.sh /duong/dan/frontend"
backend=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
frontend=$(cd -- "$1" && pwd)
[[ -f "$frontend/package-lock.json" ]] || fail "Không tìm thấy checkout frontend."
for program in docker java javac node npm python3 openssl curl; do
    command -v "$program" >/dev/null || fail "Thiếu $program."
done
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
java_version=$("$javac_bin" -version 2>&1)
[[ "$java_version" == "javac 21."* ]] || fail "Demo cần JDK 21 (kiểm tra JAVA_HOME)."

backend_port=${PFC_BACKEND_PORT:-28380}
frontend_port=${PFC_FRONTEND_PORT:-28300}
mongo_port=${PFC_MONGO_PORT:-28317}
python3 - "$backend_port" "$frontend_port" "$mongo_port" <<'PY'
import socket
import sys

ports = [int(value) for value in sys.argv[1:]]
if len(set(ports)) != 3:
    sys.exit("Ba dịch vụ cần ba cổng khác nhau.")
for port in ports:
    if not 1024 <= port <= 65535:
        sys.exit(f"Cổng ngoài phạm vi: {port}")
    with socket.socket() as sock:
        try:
            sock.bind(("127.0.0.1", port))
        except OSError:
            sys.exit(f"Cổng 127.0.0.1:{port} đang bận, hãy đổi cổng của lần chạy này.")
PY

docker_endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}' "$(docker context show)")
[[ "$docker_endpoint" == unix://* && ( -z "${DOCKER_HOST:-}" || "$DOCKER_HOST" == "$docker_endpoint" ) ]] ||
    fail "Demo cần Docker Engine cục bộ từ Ubuntu/WSL2."
docker info >/dev/null || fail "Docker chưa sẵn sàng."
logs=$(mktemp -d "${TMPDIR:-/tmp}/engolearn-pfc-demo.XXXXXX")
printf 'Log tại %s\n' "$logs"
container=
backend_pid=
frontend_pid=
cleanup() {
    if [[ -n "$frontend_pid" ]]; then kill "$frontend_pid" 2>/dev/null || true; fi
    if [[ -n "$backend_pid" ]]; then kill "$backend_pid" 2>/dev/null || true; fi
    if [[ -n "$container" ]]; then docker rm -f "$container" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# The fixture is loaded only in the empty DB created by this run.
media_dir="$backend/tools/mongo-json-importer/samples/assessment-media"
fixture_args=()
if [[ -n "${PFC_DRAFT_PACK:-}" ]]; then
    media_dir="$logs/assessment-media"
    fixture_args=(--draft-pack "$PFC_DRAFT_PACK" --output-media "$media_dir")
fi
python3 "$backend/tools/assessment/dev-placement-fixtures.py" "${fixture_args[@]}" > "$logs/fixtures.json"
container=$(docker run -d --rm --ulimit nofile=64000:64000 --publish "127.0.0.1:$mongo_port:$mongo_port" mongo:8.0.29-noble \
    mongod --bind_ip_all --port "$mongo_port" --replSet pfcdemo)
mongo=(docker exec -i "$container" mongosh --host 127.0.0.1 --port "$mongo_port" --quiet)
await_mongo() {
    for attempt in {1..45}; do
        if "${mongo[@]}" --eval "$1" </dev/null >/dev/null 2>&1; then return; fi
        sleep 1
    done
    docker logs "$container" > "$logs/mongo.log" 2>&1 || true
    fail "Mongo không sẵn sàng. Xem $logs/mongo.log."
}
await_mongo 'quit(db.runCommand({ping:1}).ok === 1 ? 0 : 1)'
"${mongo[@]}" --eval "quit(rs.initiate({_id:'pfcdemo',members:[{_id:0,host:'127.0.0.1:$mongo_port'}]}).ok === 1 ? 0 : 1)" </dev/null
await_mongo 'quit(db.hello().isWritablePrimary ? 0 : 1)'
"${mongo[@]}" --eval '
    const database = db.getSiblingDB("engolearn_pfc_local_demo");
    if (database.getCollectionNames().length !== 0) throw Error("Expected a fresh database.");
    const rows = EJSON.parse(require("fs").readFileSync("/dev/stdin", "utf8"));
    for (const [name, documents] of Object.entries(rows)) {
        const result = database.getCollection(name).insertMany(documents);
        if (!result.acknowledged) throw Error("Insert failed for " + name);
        print(name + " " + documents.length);
    }
' < "$logs/fixtures.json" > "$logs/seed.log"

(cd "$backend" && bash ./mvnw -B -ntp -DskipTests package) > "$logs/backend-build.log" 2>&1 ||
    fail "Build backend thất bại. Xem $logs/backend-build.log."
(cd "$frontend" && npm ci && npm run build) > "$logs/frontend-build.log" 2>&1 ||
    fail "Build frontend thất bại. Xem $logs/frontend-build.log."
jar="$backend/target/engolearn-0.0.1-SNAPSHOT.jar"
mkdir "$logs/runtime"
secret=$(openssl rand -hex 32)
origin="http://localhost:$frontend_port"
(
    cd "$logs/runtime"
    exec env -i PATH="$PATH" HOME="$HOME" AUTH_JWT_SECRET="$secret" \
        MONGODB_URI="mongodb://127.0.0.1:$mongo_port/?replicaSet=pfcdemo&directConnection=true" \
        APP_DB=engolearn_pfc_local_demo CORS_ALLOWED_ORIGINS="$origin" \
        PLACEMENT_MEDIA_DIRECTORY="$backend/tools/mongo-json-importer/samples/placement-media" \
        ASSESSMENT_MEDIA_DIRECTORY="$media_dir" \
        "$java_bin" -jar "$jar" --spring.profiles.active=dev \
        --server.address=127.0.0.1 --server.port="$backend_port"
) > "$logs/backend-runtime.log" 2>&1 &
backend_pid=$!

(
    cd "$frontend"
    export VITE_API_PROXY_TARGET="http://127.0.0.1:$backend_port"
    exec ./node_modules/.bin/vite preview --host localhost --port "$frontend_port" --strictPort
) > "$logs/frontend-runtime.log" 2>&1 &
frontend_pid=$!

for attempt in {1..90}; do
    kill -0 "$backend_pid" 2>/dev/null || fail "Backend đã dừng. Xem $logs/backend-runtime.log."
    kill -0 "$frontend_pid" 2>/dev/null || fail "Frontend đã dừng. Xem $logs/frontend-runtime.log."
    if curl --noproxy '*' -fsS "$origin/" >/dev/null 2>&1 &&
        curl --noproxy '*' -fsS "http://127.0.0.1:$backend_port/actuator/health/liveness" >/dev/null 2>&1; then
        printf 'Mở %s/placement\nDB thử %s. Bấm Ctrl+C để dừng và bỏ DB của lần thử.\n' \
            "$origin" "engolearn_pfc_local_demo"
        wait -n "$backend_pid" "$frontend_pid"
        exit
    fi
    sleep 1
done
fail "Dịch vụ chưa sẵn sàng. Xem hai file *-runtime.log tại $logs."
