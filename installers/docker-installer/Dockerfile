FROM vmware/photon:1.0

RUN tdnf makecache && \
    tdnf update -y tdnf && \
    tdnf install -y docker && \
    tdnf install -y wget && \
    tdnf install -y iproute2 && \
    tdnf install -y gawk && \
    tdnf install -y bc && \
    tdnf install -y perl && \
    tdnf install -y sed && \
    tdnf install -y dmidecode

RUN wget -nv -O /usr/bin/photon https://github.com/vmware/photon-controller/releases/download/v1.1.0/photon-linux64-1.1.0-5de1cb7 && \
    chmod 755 /usr/bin/photon

RUN mkdir -p /var/photon
COPY . /var/photon/
WORKDIR /var/photon
ENV PATH "$PATH:/var/photon"
