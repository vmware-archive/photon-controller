package main

import (
	"bmp/provisioning/dhcp"
	"bytes"
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"encoding/json"
	"flag"
	"os"
	"os/signal"
	"syscall"

	"golang.org/x/net/context"

	"github.com/golang/glog"
)

var (
	// bindAddress is the bind address of this service host
	bindAddress common.AddressFlag

	// authToken is the system user authorization token
	authToken string
)

func init() {
	flag.Var(&bindAddress, "bind", "Bind address")
	flag.StringVar(&authToken, "auth-token", "", "Authorization token")
}

func main() {
	services := []struct {
		uri string
		svc host.Service
	}{
		// Examples
		{
			"/core/examples",
			host.NewFactoryServiceContext(&host.ExampleFactoryService{}),
		},
		// Examples
		{
			"/core/ping",
			host.NewPingService(),
		},
	}

	var err error

	flag.Parse()

	glog.Infof("Started with %s", os.Args[1:])

	h := host.NewServiceHost()
	err = h.Initialize(bindAddress.String())
	if err != nil {
		glog.Fatalf("Error initializing: %s\n", err)
	}

	ctx := operation.SetAuthorizationToken(context.Background(), authToken)
	op := operation.NewOperation(ctx)

	var nodeState *common.ServiceHostState
	nodeState, err = host.GetServiceHostManagementState(ctx)
	if err != nil {
		glog.Fatalf("Error getting ServiceHostState: %s\n", err)
	}

	var ops []*operation.Operation
	for _, s := range services {
		op := operation.NewPost(ctx, uri.Extend(uri.Empty(), s.uri), nil)
		ops = append(ops, op)
		h.StartService(op, s.svc)
	}

	_, err = operation.Join(ops)
	if err != nil {
		glog.Fatalf("Error starting services: %s", err)
	}

	// start dhcp service
	dhcpState := &dhcp.ServiceState{
		ID: nodeState.ID,
	}
	d, _ := json.Marshal(dhcpState)
	buf := bytes.NewBuffer(d)

	startOp := op.NewPost(ctx, uri.Extend(uri.Empty(), provisioning.DhcpService), buf)
	dhcpService := dhcp.NewService()
	h.StartService(startOp, dhcpService)
	if err := startOp.Wait(); err != nil {
		// The DHCP service not starting is intentionally not an error.
		// If it were, we wouldn't be able to run this on OSX.
		glog.Errorf("Error starting service: %s\n", err)
	}

	start(h)
}

func start(h *host.ServiceHost) {
	signals := []syscall.Signal{
		syscall.SIGTERM,
		syscall.SIGINT,
	}

	sigchan := make(chan os.Signal, 1)
	for _, signum := range signals {
		signal.Notify(sigchan, signum)
	}

	go func() {
		signal := <-sigchan
		glog.Errorf("Received %s, exiting...", signal)
		h.Stop()
	}()

	h.Start()
}
