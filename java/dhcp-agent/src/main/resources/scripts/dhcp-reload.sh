#!/usr/bin/env bash

set -e

kill -SIGHUP "$(< $1)"
exit $?