#
# Docker image for building Photon Controller RPM package
#
FROM vmware/photon-controller-service-builder

RUN tdnf makecache && \
    tdnf clean all && \
    tdnf install -y createrepo && \
    tdnf install -y docker && \
    wget https://bootstrap.pypa.io/get-pip.py && python get-pip.py && rm -rf get-pip.py && \
    pip install pystache && \
    wget https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 -O /usr/bin/jq && \
    chmod 755 /usr/bin/jq && \
    curl -O https://pypi.python.org/packages/source/v/virtualenv/virtualenv-1.9.1.tar.gz && \
    tar xvfz virtualenv-1.9.1.tar.gz && \
    cd virtualenv-1.9.1 && \
    python setup.py install && \
    rm -rf virtualenv*

# Add user that has privilege to build a new rpm package
RUN useradd -s /bin/bash -G adm,wheel,systemd-journal -m rpm
