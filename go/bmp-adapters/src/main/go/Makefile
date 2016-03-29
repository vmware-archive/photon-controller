GOVENDOR ?= $(CURDIR)/_vendor
# GODCP := $(CURDIR)/../../../../../photon-provisioning/src/main/go
# GODCPVENDOR := $(GODCP)/_vendor
# GOPATH := $(CURDIR):$(GOVENDOR):$(GODCP):$(GODCPVENDOR)
GOPATH := $(CURDIR):$(GOVENDOR)

GO ?= go
GOVERSION ?= go1.5
OS := $(shell uname)
NAME := go-bmp

TEST_OPTS ?= -v

ifeq ($(OS), Linux)
CGO_ENABLED = 0
export CGO_ENABLED
endif

export GOPATH

all: check build test

check: goversion goimports govet golint

goversion:
	@( $(GO) version | grep -q $(GOVERSION) ) || ( echo "Please install $(GOVERSION) (found: $$($(GO) version))" && exit 1 )

goimports:
	$(GO) install golang.org/x/tools/cmd/goimports
	@echo checking go fmt...
#	@! $(GODCPVENDOR)/bin/goimports -d src 2>&1 | egrep -v '^$$'

govet:
	@if [ -w "$(shell $(GO) env GOTOOLDIR)" ] ; then \
		echo "$(GO) install golang.org/x/tools/cmd/vet"; \
		$(GO) install golang.org/x/tools/cmd/vet; \
	fi;
	@echo checking go vet...
	@! $(GO) vet ./... 2>&1 | egrep -v '^$$'

golint:
	$(GO) install github.com/golang/lint/golint
	@echo checking golint...
#	@$(GODCPVENDOR)/bin/golint ./... | sh -c '! grep -v comment'

errcheck:
	$(GO) install github.com/kisielk/errcheck
	@echo checking goerrcheck...
#	@$(GODCPVENDOR)/bin/errcheck ./...

govc:
	$(GO) install github.com/vmware/govmomi/govc

test:
	$(GO) test $(TEST_OPTS) ./...

godcp.darwin-amd64:
	GOOS=darwin GOARCH=amd64 $(GO) build -o bin/$(NAME)-darwin -pkgdir ./pkg bmp/cmd/godcp

godcp.linux-amd64:
	GOOS=linux GOARCH=amd64 $(GO) build -o bin/$(NAME)-linux -pkgdir ./pkg bmp/cmd/godcp

build: deps godcp.linux-amd64 godcp.darwin-amd64

package:
	mkdir -p ../resources/bin/
	cp bin/$(NAME)-darwin bin/$(NAME)-linux ../resources/bin/

check-static:
ifeq ($(OS), Linux)
	! ldd $(CURDIR)/bin/*
endif

deps:
	$(GO) get github.com/golang/glog
	$(GO) get github.com/golang/groupcache/singleflight
	$(GO) get github.com/pborman/uuid
	$(GO) get golang.org/x/net/context
	$(GO) get golang.org/x/net/ipv4
	$(GO) get golang.org/x/crypto/ssh
	$(GO) get golang.org/x/crypto/ssh/testdata
	$(GO) get github.com/golang/protobuf/proto
	$(GO) get golang.org/x/text/encoding
	$(GO) get golang.org/x/text/encoding/charmap
	$(GO) get golang.org/x/text/encoding/htmlindex
	$(GO) get golang.org/x/text/transform
	$(GO) get golang.org/x/crypto/ssh/terminal
	$(GO) get github.com/stretchr/testify/mock



clean:
	rm -rf pkg bin $(GOVENDOR)/pkg $(GOVENDOR)/bin $(DIST_DIR)

# For use by external tools such as emacs or for example:
# GOPATH=$(make gopath) go get ...
gopath:
	@echo -n $(GOPATH)
