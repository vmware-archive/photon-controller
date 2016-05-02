# Base image for all Photon-Controller services and most of dependent services.
# This image includes openjre, zookeeper and haproxy. Child images for
# haproxy and zookeeper should be created from this image and have customization
# for respective component in that child image. Ideally those child images
# would not need to install any extra package, unless that package is too
# big to be included here.
#
# Containers that should depends on this base image are
# Deployers, Housekeeper, CloudStore, Scheduler, Management-API, HaProxy, Zookeeper
#
# Containers not dependent on this image are
# Management-UI, Lightwave

FROM vmware/photon:1.0rc

RUN  tdnf update -y tdnf && \
     tdnf install -y openjre \
                    zookeeper \
                    haproxy \
                    gawk \
                    sed \
                    less \
                    net-tools \
                    iproute2 \
                    wget \
                    gzip \
                    tar \
                    && \
     tdnf clean all && \
     rpm -e --nodeps systemd && \
     rm -rf /usr/share/doc/* && \
     rm -rf /usr/share/man/* && \
     wget -nv --no-proxy -P /var/opt http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/external/esxcli_lin64/esxcli_lin64-esxcli_lin64.tgz && \
     cd /var/opt && tar xf esxcli_lin64*.tgz && rm -f esxcli_lin64*.tgz && \
     mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default

ENV JAVA_HOME /usr/java/default
ENV PATH $PATH:$JAVA_HOME/bin:/var/opt/esxcli

