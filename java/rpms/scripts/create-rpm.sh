#!/bin/bash
set -x -e

VERSION=`git rev-parse --short --verify HEAD`
ROOT=`git rev-parse --show-toplevel`
SOURCES_DIR="$ROOT/java/rpms/SOURCES"
TEMP_DIR=$(mktemp -d "$PWD/create_tar.XXXXX")
PC_TEMP_DIR="$TEMP_DIR/photon-controller-$VERSION/"
trap "rm -rf $TEMP_DIR" EXIT

# Create source tar file for use by RPM spec file
cd "$ROOT"
mkdir -p "$PC_TEMP_DIR"
mkdir -p "$SOURCES_DIR"
cp -r java "$PC_TEMP_DIR"
cp -r thrift "$PC_TEMP_DIR"
cd "$TEMP_DIR"
tar -cvzf "photon-controller-$VERSION.tar.gz" "photon-controller-$VERSION"
cp "photon-controller-$VERSION.tar.gz" "$SOURCES_DIR"

# Build the RPM package
docker run -it \
  -v ~/photon-controller:/photon-controller \
  -v ~/photon-controller/java/rpms/SOURCES/:/usr/src/photon/SOURCES \
  -v ~/photon-controller/java/build:/usr/src/photon/RPMS \
  -w /photon-controller/java/rpms \
  vmware/photon-service-builder \
  rpmbuild -ba --define="pkg_version $VERSION" SPECS/photon-controller.spec
