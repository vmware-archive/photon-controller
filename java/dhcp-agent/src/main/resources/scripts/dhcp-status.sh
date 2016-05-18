#!/usr/bin/env bash

systemctl is-active $1
exit $?