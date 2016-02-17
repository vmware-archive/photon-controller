package esxcloud

import (
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"fmt"

	"golang.org/x/net/context"

	"github.com/golang/glog"
)

type HealthService struct{}

func NewHealthService() host.Service {
	return host.NewServiceContext(&HealthService{})
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

	computeState := &provisioning.ComputeStateWithDescription{}
	err = provisioning.GetComputeStateWithDescription(ctx, req.ComputeReference, computeState)
	if err != nil {
		glog.Errorf("Error getting ComputeState: %s", err)
		op.Fail(err)
		return
	}

	// Get the ResourcePool
	resourcePool := &provisioning.ResourcePoolState{}
	rpooluri := uri.Extend(uri.Local(), computeState.ResourcePoolLink)

	if err = provisioning.GetResourcePoolState(ctx, resourcePool, rpooluri); err != nil {
		glog.Error(fmt.Sprintf("Error getting resource pool %s: %s", rpooluri, err))
		op.Fail(err)
		return
	}

	// These stats are all faked for now. TODO: Generate more meaningful, or do not provide if not required
	var cpuPct float64
	cpuPct = 20
	health := &provisioning.ComputeHealthResponse{}
	health.CPUCount = 100
	health.CPUUtilizationPct = cpuPct
	health.CPUTotalMhz = 1000 * health.CPUCount
	health.CPUUtilizationMhz = int64(float64(health.CPUTotalMhz) * cpuPct)
	health.MemoryUtilizationPct = 80
	health.TotalMemoryBytes = 1
	health.UsedMemoryBytes = 1

	op.SetBody(health)
	op.Complete()
}
