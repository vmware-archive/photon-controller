package test

import (
	"dcp/host"
	"dcp/uri"
	"flag"
	"fmt"
	"net"
	"testing"
	"time"

	"golang.org/x/net/context"

	"github.com/pborman/uuid"
)

func init() {
	// Prevent glog from writing files to /tmp
	flag.Set("logtostderr", "true")
}

type ServiceHost struct {
	*host.ServiceHost

	t      *testing.T
	doneCh chan struct{}
}

func NewServiceHost(t *testing.T) *ServiceHost {
	th := ServiceHost{
		ServiceHost: host.NewServiceHost(),

		t: t,
	}

	err := th.Initialize(fmt.Sprintf("%s:0", net.IPv4zero))
	if err != nil {
		t.Fatal(err)
	}

	// Set dcp flag so uri.WithLocalHost points to mock services
	hostURI := th.URI()
	flag.Set("dcp", hostURI.Host)

	th.doneCh = make(chan struct{})
	go func() {
		th.Start()
		close(th.doneCh)
	}()

	return &th
}

func (th *ServiceHost) Stop() error {
	err := th.ServiceHost.Stop()
	<-th.doneCh
	return err
}

func (th *ServiceHost) URI() uri.URI {
	return th.ServiceHost.URI()
}

// RemoteURI returns a URI to the TestServiceHost with a host address
// that is reachable from the given address.
func (th *ServiceHost) RemoteURI(address string) uri.URI {
	conn, err := net.Dial("udp", address)
	if err != nil {
		panic(err)
	}
	defer conn.Close()

	_, curPort, err := net.SplitHostPort(th.URI().Host)
	if err != nil {
		panic(err)
	}

	newHost, _, err := net.SplitHostPort(conn.LocalAddr().String())
	if err != nil {
		panic(err)
	}

	return uri.New(newHost, curPort)
}

// Start a service mock with given serivce state and selfLink
func (th *ServiceHost) StartMockWithSelfLink(data interface{}, selfLink string) uri.URI {
	u := uri.Extend(th.URI(), selfLink)
	s := NewMockService(data)
	if err := th.StartServiceSync(u.Path, s); err != nil {
		th.t.Fatalf("Error starting service: %s\n", err)
	}

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	select {
	case <-s.StageBarrier(host.StageAvailable):
	case <-ctx.Done():
		th.t.Fatalf("timeout starting service")
	}

	return u
}

// Start a service mock with given serivce state
func (th *ServiceHost) StartMock(data interface{}) uri.URI {
	return th.StartMockWithSelfLink(data, "/"+uuid.New())
}

// Start a factory service mock with factory service URI and a func to create service state
func (th *ServiceHost) StartFactoryMock(factoryLink string, createStatef func() interface{}) MockFactoryService {
	u := uri.Extend(th.URI(), "/"+factoryLink)
	factoryService, s := NewMockFactoryService(th, factoryLink, createStatef)
	if err := th.StartServiceSync(u.Path, s); err != nil {
		th.t.Fatalf("Error starting factory service: %s\n", err)
	}
	return factoryService
}
