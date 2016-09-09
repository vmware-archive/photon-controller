#!/bin/sh -xe

PHOTON_CONTROLLER_HOST_IP=$1
LIGHTWAVE_HOST_IP=$2

# Wait for certificates to be generated
attempts=1
certs_created="false"
total_attempts=50
while [ $attempts -lt $total_attempts ] && [ $certs_created != "true" ]; do
   if [ ! -f /etc/keys/machine.crt -o ! -f /etc/keys/machine.privkey -o ! -f /etc/keys/cacert.crt ]; then
      echo "Certificates not yet created, will try again"
      attempts=$[$attempts+1]
      sleep 5
   else
      certs_created="true"
      break
   fi
done

if [ $attempts -eq $total_attempts ]; then
   echo "Could not create certificates after $total_attempts attempts"
   exit 1
fi

# Wait for Photon Controller Service to be functioning
attempts=1
pc_running="false"
total_attempts=50
while [ $attempts -lt $total_attempts ] && [ $pc_running != "true" ]; do
   nodes=$(curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --cacert /etc/keys/cacert.crt https://$PHOTON_CONTROLLER_HOST_IP:19000/core/node-groups/default | grep AVAILABLE | wc -l)
   if [ "$nodes" != "3" ]; then
      echo "Photon Controller cluster not formed yet, will try again"
      attempts=$[$attempts+1]
      sleep 5
   else
      pc_running="true"
      break
   fi
done

if [ $attempts -eq $total_attempts ]; then
   echo "Photon Controller not functional after $total_attempts attempts"
   exit 1
fi

echo "Creating Deployment"
curl --key /etc/keys/machine.privkey --cert /etc/keys/machine.crt --cacert /etc/keys/cacert.crt -X POST   -H "Content-type: application/json" -d "{   \"state\" : \"READY\",   \"imageDataStoreNames\" : [\"datastore1\"],   \"imageDataStoreUsedForVMs\" : \"true\",   \"imageId\" : \"none\",   \"projectId\" : \"none\",   \"ntpEndpoint\" : \"\",   \"virtualNetworkEnabled\" : \"false\",   \"syslogEndpoint\" : \"\",   \"statsEnabled\" : \"false\",   \"loadBalancerEnabled\": \"false\",   \"loadBalancerAddress\" : \"$PHOTON_CONTROLLER_HOST_IP:9000\",   \"oAuthEnabled\" : \"true\",   \"oAuthTenantName\" : \"photon.local\",   \"oAuthUserName\" : \"Administrator\",   \"oAuthPassword\" : \"Admin!23\",   \"oAuthServerAddress\" : \"LIGHTWAVE_HOST_IP\",   \"oAuthServerPort\" : 443,   \"oAuthSecurityGroups\": [\"photon.local\\\admins\"],   \"documentSelfLink\" : \"deployment\"   }"   https://$PHOTON_CONTROLLER_HOST_IP:19000/photon/cloudstore/deployments

rc=$?

if [ $rc -ne 0 ]; then
    echo "Failed to create Deployment"
    exit 1
fi
