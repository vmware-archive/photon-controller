FROM vmware/photon:1.0

RUN tdnf makecache && \
    tdnf update -y tdnf && \
    tdnf install -y docker && \
    tdnf install -y wget && \
    tdnf install -y iproute2 && \
    tdnf install -y gawk && \
    tdnf install -y bc && \
    tdnf install -y perl && \
    tdnf install -y sed

RUN mkdir -p /var/photon
COPY . /var/photon/
WORKDIR /var/photon
ENV PATH "$PATH:/var/photon"
