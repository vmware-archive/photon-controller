#!/bin/bash -xe

tdnf install -y openjre
mkdir -p /usr/java && ln -s /var/opt/OpenJDK* /usr/java/default
echo 'export JAVA_HOME=/usr/java/default' >> /etc/profile
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> /etc/profile
