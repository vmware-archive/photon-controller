Environment paths

export GOPATH=~/git/photon-controller/go/bmp-adapters/src/main/go:~/git/photon-controller/go/bmp-adapters/src/main/go/_vendor/
export GOROOT=/usr/local/go
export PATH=$PATH:/usr/local/go/bin

export GO15VENDOREXPERIMENT=1

# on the go directory
# to compile
go build ./...

# to run tests
go test slingshot/ipxe -v


sudo sh -c 'export GOPATH=~/git/photon-bmp/bmp-adapters/src/main/go:~/git/photon-bmp/bmp-adapters/src/main/go/_vendor/ ; export PATH=$PATH:/usr/local/go/bin; export GO15VENDOREXPERIMENT=1;go test slingshot -v'
