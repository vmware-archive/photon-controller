VERSION=1.0.2

DOCKER_LABEL=com.vmware.photon-controller.version
DOCKER_BUILD_OPTS=--no-cache --force-rm
DOCKER_TAG=vmware/photon-controller:$(VERSION)
DOCKER_IMAGE=vmware-photon-controller-$(VERSION).tar
DOCKER_IMAGE_COMPRESSED=$(DOCKER_IMAGE).gz

all : container

container: $(DOCKER_IMAGE_COMPRESSED)

$(DOCKER_IMAGE_COMPRESSED):
	docker build $(DOCKER_BUILD_OPTS) -t $(DOCKER_TAG) . && \
	docker save $(DOCKER_TAG) > $(DOCKER_IMAGE) && \
	gzip $(DOCKER_IMAGE) && \
	docker rmi $$(docker images -f "label=$(DOCKER_LABEL)=$(VERSION)" -q)

clean:
	@for img in `docker images -f "label=$(DOCKER_LABEL)=$(VERSION)" -q`; \
        do \
            docker rmi $img; \
        done
	@rm -f $(DOCKER_IMAGE)
