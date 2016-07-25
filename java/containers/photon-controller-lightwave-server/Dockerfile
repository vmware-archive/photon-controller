OM vmware/photon-controller-service-base

ENV container=docker
VOLUME ["/sys/fs/cgroup"]

ADD *.rpm /tmp/
RUN find /tmp -iname "*.rpm" | xargs rpm -Uvh ;\
    rm -rf /tmp/*.rpm

# install systemd
RUN tdnf makecache; \
    tdnf update -y tdnf; \
    tdnf update -y rpm; \
    tdnf install -y systemd; \
    # Remove unused systemd services
    rm -f /etc/systemd/system/*.wants/*;\
    rm -f /lib/systemd/system/sysinit.target.wants/systemd-tmpfiles-setup.service;\
    rm -f /lib/systemd/system/multi-user.target.wants/*;\
    rm -f /lib/systemd/system/local-fs.target.wants/*; \
    rm -f /lib/systemd/system/sockets.target.wants/*udev*; \
    rm -f /lib/systemd/system/sockets.target.wants/*initctl*; \
    mkdir -p /var/run/sshd; chmod -rx /var/run/sshd; \
    ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key; \
    # configure journald
    sed -i 's/#Storage=auto/Storage=persistent/' /etc/systemd/journald.conf; \
    tdnf install -y procps-ng; \
    tdnf install -y commons-daemon \
                    apache-tomcat \
                    boost; \
    tdnf install -y likewise-open; \
    tdnf install -y vmware-lightwave-server; \
    rm -rf /usr/share/doc/*; \
    rm -rf /usr/share/man/*; \
    rm -rf /usr/include/*; \
    rm -rf /etc/systemd/network/10-dhcp-en.network;

EXPOSE 22 53/udp 53 88/udp 88 389 443 636 2012 2014 2020 9000 19000

ENTRYPOINT ["/usr/sbin/init"]
