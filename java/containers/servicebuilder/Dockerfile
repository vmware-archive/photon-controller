#
# Docker image for building Photon Controller java services.
#
FROM vmware/photon:1.0rc

RUN tdnf clean all \
  && tdnf update -y tdnf \
  && tdnf install -y wget \
  && tdnf install -y gzip \
  && tdnf install -y tar \
  && tdnf install -y gawk \
  && tdnf install -y sed \
  && tdnf install -y openjdk \
  && tdnf install -y openjre \
  && tdnf install -y openssh \
  && tdnf install -y rpm-build \
  && tdnf install -y apache-maven \
  && tdnf install -y git

#
# Install build essentials
#
RUN tdnf install -y linux-api-headers glibc glibc-devel zlib zlib-devel file binutils binutils-devel \
    gmp gmp-devel mpfr mpfr-devel mpc libgcc libgcc-devel libstdc++ libstdc++-devel libgomp libgomp-devel \
    gcc pkg-config ncurses bzip2 sed ncurses-devel procps-ng coreutils m4 grep readline diffutils gawk \
    findutils gettext gzip make patch util-linux tar xz libtool flex bison readline-devel lua \
    lua-devel popt popt-devel nspr sqlite-autoconf nss nss-devel bzip2-devel elfutils-libelf \
    elfutils elfutils-libelf-devel elfutils-devel expat libffi \
    libpipeline gdbm perl texinfo autoconf automake openssl openssl-devel \
    python2-libs python2-devel groff man-db man-pages cpio

#
# Build and Install thrift
#
RUN wget -nv http://archive.apache.org/dist/thrift/0.9.3/thrift-0.9.3.tar.gz \
  && tar xf thrift-*.tar.gz \
  && pushd thrift-* \
  && ./configure --without-cpp --without-csharp --with-java --without-erlang --with-python --without-lua --without-perl \
  --without-php --without-php_extension --without-ruby --without-haskell --without-go --without-d \
  && make \
  && make install \
  && popd \
  && rm -rf thrift-*

# Set up Java
RUN mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default
ENV PATH $PATH:$JAVA_HOME/bin
ENV JAVA_HOME /usr/java/default

# Add user that has privilege to build a new rpm package
RUN useradd -s /bin/bash -G adm,wheel,systemd-journal -m rpm
