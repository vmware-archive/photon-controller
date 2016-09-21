#!/usr/bin/env bash
# Script to send no-op signal to dnsmasq
# to trigger cache reload.

set -e

kill -SIGHUP "$(< $1)"
exit $?