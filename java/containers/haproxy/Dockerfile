FROM photon/servicebase

RUN tdnf install -y gzip \
  && tdnf install -y wget \
  && tdnf install -y gcc \
  && tdnf install -y make \
  && tdnf install -y glibc-devel \
  && tdnf install -y linux-api-headers \
  && tdnf install -y binutils \
  && tdnf install -y shadow

RUN wget -q http://www.haproxy.org/download/1.5/src/haproxy-1.5.11.tar.gz && \
    tar -zxf haproxy-1.5.11.tar.gz && \
    rm -f haproxy-1.5.11.tar.gz && \
    pushd /haproxy-1.5.11 && \
    make TARGET=generic USE_OPENSSL=1 && \
    make install && \
    popd && \
    rm -rf /haproxy-1.5.11 && \
    cp /usr/local/sbin/haproxy* /usr/sbin/ && \
    groupadd haproxy && \
    useradd -g haproxy haproxy && \
    mkdir -p /etc/haproxy && \
    mkdir /var/lib/haproxy

COPY haproxy.cfg /etc/haproxy/haproxy.cfg
COPY haproxy_entrypoint.sh /haproxy_entrypoint.sh
RUN chmod +x /haproxy_entrypoint.sh

WORKDIR /etc/haproxy

ENTRYPOINT /haproxy_entrypoint.sh

EXPOSE 80
EXPOSE 443
