FROM vmware/photon

RUN tdnf clean all \
  && tdnf update -y tdnf \
  && tdnf install -y tar \
  && tdnf install -y gzip \
  && tdnf install -y gawk \
  && tdnf install -y sed \
  && tdnf install -y wget \
  && tdnf install -y inetutils \
  && tdnf install -y iproute2 \
  && tdnf install -y cdrkit \
  && tdnf install -y openjdk

# Remove unneeded JDK parts, such as source, sample code, and compiler tools
RUN cd /opt/OpenJDK* && rm -rf demo && rm -rf sample && rm -f src.zip && rm -f lib/tools.jar

RUN wget -nv --no-proxy -P /opt http://artifactory.ec.eng.vmware.com/artifactory/esxcloud-archives/external/esxcli_lin64/esxcli_lin64-esxcli_lin64.tgz
RUN cd /opt && tar xf esxcli_lin64*.tgz && rm -f esxcli_lin64*.tgz

# Set up Java
RUN mkdir -p /usr/java && ln -s /opt/OpenJDK* /usr/java/default

ENV JAVA_HOME /usr/java/default
ENV PATH $PATH:$JAVA_HOME/bin:/opt/esxcli

