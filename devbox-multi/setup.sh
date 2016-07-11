#!/bin/bash -xe

# this should be replaced with building the rpm
rm -rf photon-controller/release

##
## building the java code
## should be replace with building the RPM
##
pushd ../java
rm -rf photon-controller-core/build/container/release
./gradlew distTar
cp -R photon-controller-core/build/container/release ../devbox-voltron/photon-controller
popd

##
## building the agent vib
##
pushd ../python
rm -rf dist
make vib-only
popd

chmod -R +x photon-controller/release/bin

vagrant destroy -f && vagrant up


# create deployment document
curl -X POST \
  -H "Content-type: application/json" -d "{ \
  \"state\" : \"READY\",
  \"imageDataStoreNames\" : [\"datastore1\"], \
  \"imageDataStoreUsedForVMs\" : \"true\", \
  \"imageId\" : \"none\", \
  \"projectId\" : \"none\", \
  \"ntpEndpoint\" : \"1.1.1.1\", \
  \"virtualNetworkEnabled\" : \"false\", \
  \"syslogEndpoint\" : \"1.1.1.1\", \
  \"statsEnabled\" : \"false\", \
  \"loadBalancerEnabled\": \"false\", \
  \"loadBalancerAddress\" : \"172.31.253.65:80\", \
  \"documentSelfLink\" : \"deployment\"
  "} \
  http://172.31.253.66:19000/photon/cloudstore/deployments

# add host
curl -X POST \
  -H "Content-type: application/json" -d "{ \
    \"address\" : \"10.146.36.36\", \
    \"username\" : \"root\", \
    \"password\" : \"ca\$hc0w\", \
    \"usageTags\" : [\"CLOUD\"] \
  "} \
  http://172.31.253.65/deployments/deployment/hosts

# run tests
