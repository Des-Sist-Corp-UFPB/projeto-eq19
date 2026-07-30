#!/bin/sh
set -eu

java -jar /app/backend.jar &
java_pid=$!

nginx -g 'daemon off;' &
nginx_pid=$!

shutdown() {
    trap - TERM INT EXIT
    kill -TERM "$java_pid" "$nginx_pid" 2>/dev/null || true
    wait "$java_pid" 2>/dev/null || true
    wait "$nginx_pid" 2>/dev/null || true
}

trap 'shutdown; exit 143' TERM INT
trap shutdown EXIT

while kill -0 "$java_pid" 2>/dev/null && kill -0 "$nginx_pid" 2>/dev/null; do
    sleep 1
done

if ! kill -0 "$java_pid" 2>/dev/null; then
    wait "$java_pid"
    exit $?
fi

wait "$nginx_pid"
