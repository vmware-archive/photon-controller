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
                    openssh \
                    sshpass \
                    && \
     tdnf clean all && \
     rpm -e --nodeps systemd && \
     rm -rf /usr/share/doc/* && \
     rm -rf /usr/share/man/* && \
     mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default && \
     mkdir -p /var/esxcloud/data/zookeeper && \
     groupadd haproxy && \
     useradd -r -M -g haproxy -s /sbin/nologin haproxy && \
     mkdir -p /etc/haproxy && \
     mkdir -p /var/lib/haproxy

ENV CLASSPATH /usr/lib/zookeeper-3.4.8.jar:/usr/lib/log4j-1.2.16.jar:/usr/lib/jline-0.9.94.jar:/usr/lib/netty-3.7.0.Final.jar:/usr/lib/slf4j-api-1.6.1.jar:/usr/lib/slf4j-log4j12-1.6.1.jar
ENV JAVA_HOME /usr/java/default
ENV PATH $PATH:$JAVA_HOME/bin:/var/opt/esxcli

