#!/bin/bash
set -x -e

VERSION=$1
DEBUG=$2

chown root:root ./SPECS/*
chown root:root /usr/src/photon/SOURCES/*

if [ "$DEBUG" == "true" ]; then
  bash
else
  rpmbuild -ba --define="pkg_version $VERSION" ./SPECS/photon-controller.spec
  createrepo --database /usr/src/photon/RPMS
  tar -czf ../build/photon-controller-rpmrepo-"$VERSION".tar.gz -C /usr/src/photon/ RPMS
fi
