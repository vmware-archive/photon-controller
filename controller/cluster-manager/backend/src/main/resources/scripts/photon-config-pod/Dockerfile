FROM vmware/photon:1.0

RUN tdnf install -y sed wget && \
    wget http://stedolan.github.io/jq/download/linux64/jq -O /usr/bin/jq && chmod +x /usr/bin/jq && \
    mkdir /var/photon

COPY update-config.sh /var/photon/
COPY photon.cfg /var/photon/

WORKDIR /var/photon
ENTRYPOINT ["/var/photon/update-config.sh"]
