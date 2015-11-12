FROM photon/servicebase

RUN tdnf install -y gcc \
  && tdnf install -y make \
  && tdnf install -y glibc-devel \
  && tdnf install -y linux-api-headers \
  && tdnf install -y binutils \
  && tdnf install -y shadow

RUN wget -q http://www.haproxy.org/download/1.5/src/haproxy-1.5.11.tar.gz && \
  tar xzf haproxy-*.tar.gz && \
  pushd haproxy-* && \
  make TARGET=generic USE_OPENSSL=1 && \
  make install && \
  popd && \
  rm -rf haproxy-* && \
  cp /usr/local/sbin/haproxy* /usr/sbin/ && \
  groupadd haproxy && \
  useradd -r -M -g haproxy -s /sbin/nologin haproxy && \
  mkdir -p /etc/haproxy && \
  mkdir -p /var/lib/haproxy

WORKDIR /etc/haproxy

EXPOSE 80
EXPOSE 443
