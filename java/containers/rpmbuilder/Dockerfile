#
# Docker image for building Photon Controller RPM package
#
FROM vmware/photon-controller-service-builder

RUN tdnf install -y ruby && \
    gem install mustache && \
    wget https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 -O /usr/bin/jq && \
    chmod 755 /usr/bin/jq

# Add user that has privilege to build a new rpm package
RUN useradd -s /bin/bash -G adm,wheel,systemd-journal -m rpm
