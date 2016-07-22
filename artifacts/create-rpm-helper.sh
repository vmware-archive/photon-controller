#!/bin/bash
set -x -e

VERSION=$1
COMMIT=$2
COMMIT_COUNT=$3
DEBUG=$4

cd rpms
chown root:root ./SPECS/*
chown root:root /usr/src/photon/SOURCES/*

if [ "$DEBUG" == "true" ]; then
  bash
else
  rpmbuild -ba \
    --define="pkg_version $VERSION" \
    --define="pkg_commit $COMMIT" \
    --define="pkg_commit_count $COMMIT_COUNT" \
    ./SPECS/photon-controller.spec

  createrepo --database /usr/src/photon/RPMS

  tar -czf ../build/photon-controller-rpmrepo-"$VERSION".tar.gz -C /usr/src/photon/ RPMS
fi
