FROM photon/servicebase

ENV container=docker

VOLUME ["/sys/fs/cgroup"]

RUN tdnf makecache

# install systemd
# we need systemd to run lightwave client (which runs few other processes) and photon controller in the same container
RUN tdnf install -y systemd;\
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
    tdnf install -y vmware-lightwave-clients

ADD photon-controller.service /usr/lib/systemd/system/
RUN ln -s /usr/lib/systemd/system/photon-controller.service \
          /etc/systemd/system/multi-user.target.wants/photon-controller.service

ENV PATH $PATH:/opt/vmware/bin:/opt/likewise/bin

EXPOSE 22 53/udp 53 88/udp 88 389 636 2012 2014 2020

ENTRYPOINT ["/usr/sbin/init"]
