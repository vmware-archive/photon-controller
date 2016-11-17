FROM vmware/photon:1.0
ENV container=docker
LABEL com.vmware.photon-controller.version "1.0.3"

VOLUME ["/sys/fs/cgroup"]

RUN printf '[photon-controller]\n\
name=VMware Photon Controller 1.0(x86_64)\n\
baseurl=https://dl.bintray.com/vmware/photon_controller\n\
gpgkey=file:///etc/pki/rpm-gpg/VMWARE-RPM-GPG-KEY\n\
gpgcheck=1\n\
enabled=1\n\
skip_if_unavailable=True\n' >> /etc/yum.repos.d/photon-controller.repo

# Note: systemd is needed by Lightwave client

RUN tdnf makecache \
 && tdnf clean all \
 && tdnf install -y systemd \
 && rm -f /etc/systemd/system/*.wants/* \
 && rm -f /lib/systemd/system/sysinit.target.wants/systemd-tmpfiles-setup.service \
 && rm -f /lib/systemd/system/multi-user.target.wants/* \
 && rm -f /lib/systemd/system/local-fs.target.wants/* \
 && rm -f /lib/systemd/system/sockets.target.wants/*udev* \
 && rm -f /lib/systemd/system/sockets.target.wants/*initctl* \
 && tdnf install -y sed \
 && sed -i 's/#Storage=auto/Storage=persistent/' /etc/systemd/journald.conf \
 && tdnf install -y wget \
 && tdnf install -y gzip \
 && tdnf install -y tar \
 && tdnf install -y gawk \
 && tdnf install -y iputils \
 && tdnf install -y net-tools \
 && tdnf install -y iproute2 \
 && tdnf install -y cdrkit \
 && tdnf install -y procps-ng \
 && tdnf install -y openjre \
 && tdnf install -y openjdk \
 && tdnf install -y openssh \
 && mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default \
 && mkdir -p /var/run/sshd; chmod -rx /var/run/sshd \
 && ssh-keygen -t rsa -f /etc/ssh/ssh_host_rsa_key \
 && tdnf install -y sshpass \
 && tdnf install -y likewise-open-6.2.10 \
 && tdnf install -y vmware-lightwave-clients-6.6.2 \
 && tdnf install -y photon-controller-1.0.3 \
 && rm -rf /usr/share/doc/* \
 && rm -rf /usr/share/man/* \
 && rm -rf /usr/include/*

ENV JAVA_HOME /usr/java/default
ENV PATH $PATH:/opt/vmware/bin:/opt/likewise/bin

# Ports
# 2020  : Lightwave Authentication Framework Service
# 9000  : Photon Controller API endpoint
# 19000 : Photon Controller Cloudstore/Xenon endpoint
#
EXPOSE 2020 9000 19000

ENTRYPOINT ["/usr/sbin/init"]
