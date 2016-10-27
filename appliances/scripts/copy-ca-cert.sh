#!/bin/bash -x

CA_CERT_FILE=$1

echo "Copying CA Cert to ca-bundle"
if [ -f $CA_CERT_FILE ]
  then
    cat $CA_CERT_FILE >> /etc/pki/tls/certs/ca-bundle.crt
    echo "Copying done"
    echo "Restarting docker"
    systemctl restart docker
    echo "Docker restarted"
fi
echo "Done"
