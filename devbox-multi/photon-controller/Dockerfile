FROM photon/photon-controller-core
COPY release/ /usr/lib/esxcloud/photon-controller-core/
COPY vib/ /var/esxcloud/packages
COPY configurations/ /etc/esxcloud-deployer/configurations/
COPY scripts/deployer/ /usr/lib/esxcloud/photon-controller-core/scripts
#COPY cm/scripts/ /usr/lib/esxcloud/photon-controller-core/scripts/clusters
RUN tdnf install -y openssh && tdnf install -y sshpass && chmod -R a+r /usr/lib
