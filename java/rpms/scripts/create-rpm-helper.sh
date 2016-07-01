#!/bin/bash
set -x -e

VERSION=$1
chown root:root ./SPECS/*
chown root:root /usr/src/photon/SOURCES/*
rpmbuild -ba --define="pkg_version $VERSION" ./SPECS/photon-controller.spec
createrepo --database /usr/src/photon/RPMS
