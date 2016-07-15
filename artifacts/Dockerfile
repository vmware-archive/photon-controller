FROM scratch

MAINTAINER tliaqat@vmware.com
ADD ./build/docker/photon-controller-docker*.tar /

ENV JAVA_HOME /usr/java/default
ENV PATH=$PATH:$JAVA_HOME
CMD ["bash", "--login"]
