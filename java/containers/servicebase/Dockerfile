FROM vmware/photon:1.0rc

RUN tdnf clean all \
  && tdnf install -y wget \
  && tdnf install -y gzip \
  && tdnf install -y tar \
  && tdnf install -y gawk \
  && tdnf install -y sed \
  && tdnf install -y iputils \
  && tdnf install -y net-tools \
  && tdnf install -y iproute2 \
  && tdnf install -y cdrkit \
  && tdnf install -y openjre \
  && tdnf install -y openssh \
  && tdnf install -y sshpass

# Set up Java
RUN mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default

ENV JAVA_HOME /usr/java/default
