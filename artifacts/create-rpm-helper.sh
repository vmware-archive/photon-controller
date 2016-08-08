#!/bin/bash
set -x -e

VERSION=$1
COMMIT=$2
COMMIT_COUNT=$3
DEBUG=$4

USER=`stat -c '%u' .`
GROUP=`stat -c '%g' .`

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
fi

chown -R ${USER}:${GROUP} ./SPECS/*
chown -R ${USER}:${GROUP} /usr/src/photon
