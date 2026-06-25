#!/bin/sh
set -e
java -jar /app/backend.jar &
exec nginx -g 'daemon off;'
