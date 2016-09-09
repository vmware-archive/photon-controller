#!/bin/sh +xe

./make-vms.sh
./make-lw-cluster.sh
./make-pc-cluster.sh
./make-deployment.sh
