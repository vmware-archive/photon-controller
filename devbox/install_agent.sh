#!/bin/bash

ENVOY_VIB_URL=${ENVOY_VIB_URL:="http://s3.amazonaws.com/photon-platform/artifacts/vibs/envoy/develop/latest/vmware-envoy-latest.vib"}
LIGHTWAVE_VIB_URL=${LIGHTWAVE_VIB_URL:="http://s3.amazonaws.com/photon-platform/artifacts/vibs/lightwave/develop/latest/VMware-lightwave-esx-latest.vib"}
HOST_VIBS_DIR_PATH="/tmp/photon-controller-vibs"
ROOT=`git rev-parse --show-toplevel`
SCRIPTS_PATH="${ROOT}"/controller/deployer/src/main/resources/scripts
TEMP_DIR="${ROOT}/temp_vibs_dir"
VIBNAMES=("photon-controller-agent" "envoy" "lightwave-esx" )

rm -rf "${TEMP_DIR}"
mkdir -p "${TEMP_DIR}"

cd "${TEMP_DIR}"

#Download pre-build ENVOY and LIGHTWAVE vibs from artifactory.
wget "${ENVOY_VIB_URL}"
wget "${LIGHTWAVE_VIB_URL}"

#Copy the photon-controller agent vib
cp "${ROOT}"/agent/dist/*.vib ./

ENVOY_VIB=`ls | grep envoy`
LIGHTWAVE_VIB=`ls | grep lightwave`
PHOTON_CONTROLLER_AGENT_VIB=`ls | grep photon`

##Delete the vibs from the host
for vibname in ${VIBNAMES[@]};do
   "${SCRIPTS_PATH}"/esx-delete-agent2 $ESX_IP $ESX_USERNAME "$ESX_PASSWORD" $vibname
done

##Upload the vibs to the host in order lightwave, envoy, photon-controller-agent
"${SCRIPTS_PATH}"/esx-upload-vib $ESX_IP $ESX_USERNAME "$ESX_PASSWORD" ${ENVOY_VIB} $HOST_VIBS_DIR_PATH/${ENVOY_VIB}
"${SCRIPTS_PATH}"/esx-upload-vib $ESX_IP $ESX_USERNAME "$ESX_PASSWORD" ${LIGHTWAVE_VIB} $HOST_VIBS_DIR_PATH/${LIGHTWAVE_VIB}
"${SCRIPTS_PATH}"/esx-upload-vib $ESX_IP $ESX_USERNAME "$ESX_PASSWORD" ${PHOTON_CONTROLLER_AGENT_VIB} $HOST_VIBS_DIR_PATH/${PHOTON_CONTROLLER_AGENT_VIB}

#Install the vibs to the host
"${SCRIPTS_PATH}"/esx-install-agent2 $ESX_IP $ESX_USERNAME "$ESX_PASSWORD" $HOST_VIBS_DIR_PATH/${LIGHTWAVE_VIB} $LW_DOMAIN_NAME $PUBLIC_LW_NETWORK_IP "$LW_PASSWORD"
"${SCRIPTS_PATH}"/esx-install-agent2 $ESX_IP $ESX_USERNAME "$ESX_PASSWORD" $HOST_VIBS_DIR_PATH/${ENVOY_VIB} $LW_DOMAIN_NAME $PUBLIC_LW_NETWORK_IP "$LW_PASSWORD"
"${SCRIPTS_PATH}"/esx-install-agent2 $ESX_IP $ESX_USERNAME "$ESX_PASSWORD" $HOST_VIBS_DIR_PATH/${PHOTON_CONTROLLER_AGENT_VIB} $LW_DOMAIN_NAME $PUBLIC_LW_NETWORK_IP "$LW_PASSWORD"

cd "${ROOT}"
