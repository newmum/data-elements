#!/bin/sh
# Native renewal entry point for the demo IP certificate. Keys stay on the server.
set -eu
umask 077

case "${1:-renew}" in
    reload)
        /opt/nginx/sbin/nginx -t
        /opt/nginx/sbin/nginx -s reload
        ;;
    renew)
        exec 9>/run/lock/data-elements-acme-renew.lock
        flock -n 9 || exit 0
        /opt/acme.sh/acme.sh --cron --home /opt/acme.sh \
            --config-home /etc/acme --cert-home /etc/acme/certs
        ;;
    *)
        echo "Usage: $0 [renew|reload]" >&2
        exit 2
        ;;
esac
