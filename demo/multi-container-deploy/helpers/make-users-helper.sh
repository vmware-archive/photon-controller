#!/bin/bash +xe

if [ "$DEBUG" == "1" ]; then
  set -xe
fi

PHOTON_USER=$1
PHOTON_USER_PASSWORD=$2
PHOTON_USER_FIRST_NAME=$PHOTON_USER
PHOTON_USER_LAST_NAME=$PHOTON_USER
LIGHTWAVE_PASSWORD=$3

echo "Creating Groups in Lightwave Directory"
/opt/vmware/bin/dir-cli ssogroup create --name admins --password $LIGHTWAVE_PASSWORD

rc=$?
if [ $rc -ne 0 ]; then
    echo "Failed to create group [Name: ssogroup]"
    exit 1
fi

echo "Creating Users  in Lightwave Directory"
/opt/vmware/bin/dir-cli user create --account $PHOTON_USER \
                                    --user-password $PHOTON_USER_PASSWORD \
                                    --first-name $PHOTON_USER_FIRST_NAME \
                                    --last-name $PHOTON_USER_LAST_NAME \
                                    --password $LIGHTWAVE_PASSWORD
rc=$?
if [ $rc -ne 0 ]; then
    echo "Failed to create user [ Name: $PHOTON_USER ]"
    exit 1
fi

echo "Adding Users to Groups in Lightwave Directory"

/opt/vmware/bin/dir-cli group modify --name admins \
                                     --add $PHOTON_USER \
                                     --password $LIGHTWAVE_PASSWORD
rc=$?
if [ $rc -ne 0 ]; then
    echo "Failed to add user [$PHOTON_USER] to group [ admins ]"
    exit 1
fi
