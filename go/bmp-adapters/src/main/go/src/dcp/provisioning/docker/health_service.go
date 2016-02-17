package docker

import (
	"bytes"
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"fmt"

	"golang.org/x/net/context"

	"github.com/golang/glog"
)

type HealthService struct {
	GetHealthState func(context.Context, *provisioning.ComputeStateWithDescription, *provisioning.ComputeHealthResponse) error
}

func NewHealthService() host.Service {
	return host.NewServiceContext(&HealthService{
		GetHealthState: getHealthState,
	})
}

func (s *HealthService) GetState() interface{} {
	return &common.ServiceDocument{}
}

func (s *HealthService) HandlePatch(ctx context.Context, op *operation.Operation) {
	req := &provisioning.ComputeHealthRequest{}
	err := op.DecodeBody(req)
	if err != nil {
		op.Fail(err)
		return
	}

	state := &provisioning.ComputeStateWithDescription{}
	err = provisioning.GetComputeStateWithDescription(ctx, req.ComputeReference, state)
	if err != nil {
		glog.Errorf("Error getting ComputeState: %s", err)
		op.Fail(err)
		return
	}

	health := &provisioning.ComputeHealthResponse{
		HealthState: provisioning.HealthStateUnknown,
	}

	err = s.GetHealthStats(ctx, state, health)
	if err != nil {
		glog.Errorf("Error getting %s health stats: %s", state.ID, err)
	}

	err = s.GetHealthState(ctx, state, health)
	if err != nil {
		glog.Errorf("Error getting %s health state: %s", state.ID, err)
	}

	op.SetBody(health)
	op.Complete()
}

func (s *HealthService) GetHealthStats(ctx context.Context, state *provisioning.ComputeStateWithDescription, health *provisioning.ComputeHealthResponse) error {
	client, err := sshConnect(ctx, state)
	if err != nil {
		return err
	}
	defer client.Close()

	dockerInstance, err := getDockerInstance(ctx, &state.ComputeState)
	if err != nil {
		return err
	}

	var stdout bytes.Buffer
	err = client.RunWithRetry(fmt.Sprintf("%s stats --no-stream=true %s", dockerInstance, state.ID), "", &stdout, nil)
	if err != nil {
		return err
	}

	err = parseStats(stdout.Bytes(), health)

	if err != nil {
		return err
	}

	health.HealthState = provisioning.HealthStateHealthy

	return nil
}

func getHealthState(ctx context.Context, state *provisioning.ComputeStateWithDescription, health *provisioning.ComputeHealthResponse) error {
	return nil
}

// There is no json output option similar to 'docker inspect'.
// For now, we just do the reverse of the docker's
// api/client/stats/containerStats.Display()

const (
	KB = 1000
	MB = 1000 * KB
	GB = 1000 * MB
	TB = 1000 * GB
	PB = 1000 * TB
)

var (
	unitMap = map[string]int64{"KB": KB, "MB": MB, "GB": GB, "TB": TB, "PB": PB}
)

type memValue struct {
	value float64
	units string
}

func memToBytes(m memValue) int64 {
	if mul, ok := unitMap[m.units]; ok {
		m.value *= float64(mul)
	}

	return int64(m.value)
}

func parseStats(buf []byte, health *provisioning.ComputeHealthResponse) error {
	var id string
	var memUsed, memTotal memValue

	lines := bytes.SplitN(buf, []byte("\n"), 2)
	if len(lines) != 2 {
		return fmt.Errorf("malformed stats output: %s", string(buf))
	}

	line := string(lines[1])

	_, err := fmt.Sscanf(line, "%s %f%% %f %s / %f %s %f%%",
		&id, &health.CPUUtilizationPct,
		&memUsed.value, &memUsed.units,
		&memTotal.value, &memTotal.units,
		&health.MemoryUtilizationPct)

	if err != nil {
		return fmt.Errorf("error parsing stats [%s]: %s", line, err)
	}

	health.UsedMemoryBytes = memToBytes(memUsed)
	health.TotalMemoryBytes = memToBytes(memTotal)

	return nil
}
