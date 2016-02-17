package kubernetes

import (
	"bytes"
	"dcp/client"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/provisioning/docker"
	"dcp/uri"
	"fmt"

	"golang.org/x/net/context"
)

func NewHealthService() host.Service {
	return host.NewServiceContext(&docker.HealthService{
		GetHealthState: getHealthState,
	})
}

func getHealthState(ctx context.Context, state *provisioning.ComputeStateWithDescription, health *provisioning.ComputeHealthResponse) error {
	health.HealthState = provisioning.HealthStateUnhealthy

	u, err := uri.Parse(fmt.Sprintf("https://%s:6443/healthz", state.Address))
	if err != nil {
		return err
	}

	op := operation.NewGet(ctx, u)
	if err := client.Send(op).Wait(); err != nil {
		return err
	}

	body := op.GetBody().(*bytes.Buffer)
	res := body.String()

	if res == "ok" {
		health.HealthState = provisioning.HealthStateHealthy
	} else {
		return fmt.Errorf("healthz reports: '%s'", res)
	}

	return nil
}
