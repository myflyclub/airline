#!/bin/sh
set -eu

: "${BACKEND_URL:=http://backend:8000}"

envsubst '$BACKEND_URL' \
  < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
