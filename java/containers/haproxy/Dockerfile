FROM photon/servicebase

RUN tdnf install -y haproxy && \
    groupadd haproxy && \
    useradd -g haproxy haproxy && \
    mkdir /var/lib/haproxy

WORKDIR /etc/haproxy

EXPOSE 80
EXPOSE 443
EXPOSE 8080
