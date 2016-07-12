
include common.mk


# Disable parallel build even with the -j option specified.
.NOTPARALLEL:

PACKAGES := $(shell find src -mindepth 2 -maxdepth 2 -type f -name Makefile \
              | xargs -n1 dirname)

ifdef XUNIT
	TEST_OPTS += --with-xunit
endif

ifdef DISABLE_HYPERVISOR_LOGGING
	TEST_OPTS += --logging-filter=-__hypervisor__
endif

ifndef BRANCH
	BRANCH=$(shell git rev-parse --abbrev-ref HEAD)
endif

# we are using this variable to set the version within the vib descriptor.xml
ifndef GERRIT_BRANCH
	GERRIT_BRANCH=$(BRANCH)
endif

ifndef COMMIT_HASH
	COMMIT_HASH=$(shell git rev-parse --short HEAD)
endif

# this is the version the agent reports through thrift
ifndef VERSION
	VERSION=$(GERRIT_BRANCH)-$(COMMIT_HASH)
endif

export VERSION
export GERRIT_BRANCH
export COMMIT_HASH

export TEST_OPTS
export PROMOTE_NUMBER

all: test

clean-src/%: src/%
	@echo "$(HEADER)Clean $<$(NORMAL)"
	@$(MAKE) -C $< clean

CLEAN_TARGETS := $(patsubst %,clean-%,$(PACKAGES))
clean: $(CLEAN_TARGETS)
	rm -rf dist develop

.PHONY: clean clean-src/%

develop-src/%: src/%
	@echo "$(HEADER)Develop $<$(NORMAL)"
	@$(MAKE) -C $< develop

DEVELOP_TARGETS := $(patsubst %,develop-%,$(PACKAGES))
develop: $(DEVELOP_TARGETS)

.PHONY: develop develop-src/%

test-src/%: src/%
	@echo "$(HEADER)Test $<$(NORMAL)"
	@$(MAKE) -C $< test

TEST_TARGETS := $(patsubst %,test-%,$(PACKAGES))
test: $(TEST_TARGETS)

.PHONY: test test-src/%

coverage-src/%: src/%
	@echo "$(HEADER)Coverage $<$(NORMAL)"
	@$(MAKE) -C $< coverage

COVERAGE_TARGETS := $(patsubst %,coverage-%,$(PACKAGES))
coverage: $(COVERAGE_TARGETS)

.PHONY: coverage test-src/%

check-src/%: src/%
	@echo "$(HEADER)Check $<$(NORMAL)"
	@$(MAKE) -C $< check

CHECK_TARGETS := $(patsubst %,check-%,$(PACKAGES))
check: $(CHECK_TARGETS)

.PHONY: check check-src/%

dist-src/%: src/%
	@echo "$(HEADER)Dist $<$(NORMAL)"
	@$(MAKE) -C $< DIST=$(CURDIR)/dist dist

DIST_TARGETS := $(patsubst %,dist-%,$(PACKAGES))
dist: $(DIST_TARGETS)

.PHONY: dist dist-src/%

vib-only: dist/photon-controller-agent-%-${ESX_VERSION}.vib

dist/photon-controller-agent-%-${ESX_VERSION}.vib: dist
	vib/create_vib.sh

.PHONY: version vib-only

deploy: dist/photon-controller-agent-%-${ESX_VERSION}.vib $(TEST_FILES)
ifdef REMOTE_SERVER
	./vib/deploy_vib.sh -t $(REMOTE_SERVER) -n photon-controller-agent -f dist/photon-controller-agent-*-${ESX_VERSION}.vib
endif

.PHONY: deploy

vib: version deploy

.PHONY: vib

version:
	@$(foreach package,$(PACKAGES),echo $$VERSION > $(package)/VERSION;)

.PHONY: version
