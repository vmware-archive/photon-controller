FROM photon/servicebase

RUN tdnf clean all \
  && tdnf install -y zookeeper

RUN mkdir -p /var/esxcloud/data/zookeeper

COPY zoo.cfg /etc/zookeeper/zoo.cfg
COPY log4j.properties /etc/zookeeper/log4j.properties

EXPOSE 2181 2888 3888
WORKDIR /usr/etc/zookeeper

