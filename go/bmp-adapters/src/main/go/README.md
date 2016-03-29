# Development Setup
## Environment paths
```
export PROJECT=/path/to/photon-controller
export GOPATH=${PROJECT}/go/bmp-adapters/src/main/go:${PROJECT}/go/bmp-adapters/src/main/go/_vendor/
export GOROOT=/usr/local/go
export PATH=$PATH:/usr/local/go/bin
export GO15VENDOREXPERIMENT=1
```

## Building
```
cd ${PROJECT}/go/bmp-adapters/src/main/go
make build
```

## Testing
```
cd ${PROJECT}/go/bmp-adapters/src/main/go
make test
```
to run only the slingshot tests you can run
```
go test slingshot/ipxe -v
```

## Running
```
sudo sh -c 'export GOPATH=~/git/photon-bmp/bmp-adapters/src/main/go:~/git/photon-bmp/bmp-adapters/src/main/go/_vendor/; export PATH=$PATH:/usr/local/go/bin; export GO15VENDOREXPERIMENT=1; go test slingshot -v'
```
