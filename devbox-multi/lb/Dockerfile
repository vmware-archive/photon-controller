FROM photon/servicebase

RUN tdnf makecache && \
    tdnf install -y haproxy && \
    groupadd haproxy && \
    useradd -g haproxy haproxy && \
    mkdir /var/lib/haproxy

COPY haproxy.cfg /etc/haproxy/

WORKDIR /etc/haproxy

EXPOSE 80
EXPOSE 443
EXPOSE 8080

ENTRYPOINT ["/usr/sbin/haproxy", "-d", "-f", "/etc/haproxy/haproxy.cfg", "-p", "/var/run/haproxy.pid"]
