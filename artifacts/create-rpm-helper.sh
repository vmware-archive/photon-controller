#!/bin/bash
set -x -e

VERSION=$1
COMMIT=$2
COMMIT_COUNT=$3
DEBUG=$4

USER=`stat -c '%u' .`
GROUP=`stat -c '%g' .`

TEMP_DIR=$(mktemp -d)
cp -r rpms/SPECS $TEMP_DIR/
mkdir -p /usr/src/photon
cp -r /SOURCES /usr/src/photon/
cd $TEMP_DIR

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

chown -R ${USER}:${GROUP} /usr/src/photon/RPMS/*
cp -r /usr/src/photon/RPMS/* /BUILD/RPMS/
