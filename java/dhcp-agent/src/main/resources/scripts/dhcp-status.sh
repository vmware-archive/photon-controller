#!/usr/bin/env bash

set -e

systemctl is-active $1
exit $?