FROM vmware/servicebase

RUN tdnf clean all \
  && tdnf install -y zookeeper

RUN mkdir -p /var/esxcloud/data/zookeeper

COPY config/zoo.cfg /etc/zookeeper/zoo.cfg
COPY config/log4j.properties /etc/zookeeper/log4j.properties

EXPOSE 2181 2888 3888
WORKDIR /usr/etc/zookeeper

