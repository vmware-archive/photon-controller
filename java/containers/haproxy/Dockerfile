FROM photon/servicebase

RUN tdnf install -y haproxy

COPY haproxy.cfg /etc/haproxy/haproxy.cfg
COPY run.sh /run.sh
RUN chmod +x /run.sh

WORKDIR /etc/haproxy

ENTRYPOINT /run.sh

EXPOSE 80
EXPOSE 443
