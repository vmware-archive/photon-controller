FROM vmware/photon-controller-service-base

ENV container=docker

VOLUME ["/sys/fs/cgroup"]

ADD *.rpm /tmp/
RUN find /tmp -iname "*.rpm" | xargs rpm -Uvh

# Install systemd
# we need systemd to run lightwave client (which runs few other processes) and photon controller in the same container
RUN tdnf makecache; \
    tdnf install -y systemd;\
    rm -f /etc/systemd/system/*.wants/*;\
    rm -f /lib/systemd/system/sysinit.target.wants/systemd-tmpfiles-setup.service;\
    rm -f /lib/systemd/system/multi-user.target.wants/*;\
    rm -f /lib/systemd/system/local-fs.target.wants/*; \
    rm -f /lib/systemd/system/sockets.target.wants/*udev*; \
    rm -f /lib/systemd/system/sockets.target.wants/*initctl*

RUN mkdir -p /var/run/sshd; chmod -rx /var/run/sshd
RUN ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key

# configure journald
RUN sed -i 's/#Storage=auto/Storage=persistent/' /etc/systemd/journald.conf

# install lightwave
RUN tdnf install -y procps-ng; \
    tdnf install -y likewise-open; \
    tdnf install -y boost; \
    tdnf install -y vmware-lightwave-clients-6.6.0

ENV PATH $PATH:/opt/vmware/bin:/opt/likewise/bin

ENTRYPOINT ["/usr/sbin/init"]
