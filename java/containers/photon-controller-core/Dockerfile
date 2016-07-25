FROM vmware/photon-controller-service-base

ADD *.rpm /tmp/
RUN find /tmp -iname "*.rpm" | xargs rpm -Uvh

EXPOSE 9000 19000

ENTRYPOINT ["/usr/bin/run.sh"]
