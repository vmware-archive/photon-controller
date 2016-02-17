package esx

//import (
//	"dcp/common"
//	"dcp/host"
//	"dcp/operation"
//	"dcp/provisioning"
//
//	"golang.org/x/net/context"
//
//	"github.com/golang/glog"
//)
//
//type HealthService struct{}
//
//func NewHealthService() host.Service {
//	return host.NewServiceContext(&HealthService{})
//}
//
//func (s *HealthService) GetState() interface{} {
//	return &common.ServiceDocument{}
//}
//
//func isComputeHost(state *provisioning.ComputeStateWithDescription) bool {
//	kids := state.Description.SupportedChildren
//	if kids == nil {
//		return false
//	}
//
//	for _, k := range kids {
//		if k == provisioning.ComputeTypeVMGuest {
//			return true
//		}
//	}
//
//	return false
//}
//
//func (s *HealthService) HandlePatch(ctx context.Context, op *operation.Operation) {
//	req := &provisioning.ComputeHealthRequest{}
//	err := op.DecodeBody(req)
//	if err != nil {
//		op.Fail(err)
//		return
//	}
//
//	state := &provisioning.ComputeStateWithDescription{}
//	err = provisioning.GetComputeStateWithDescription(ctx, req.ComputeReference, state)
//	if err != nil {
//		glog.Errorf("Error getting ComputeState: %s", err)
//		op.Fail(err)
//		return
//	}
//
//	var get func(*Client, *provisioning.ComputeHealthResponse) error
//	var kind string
//
//	if isComputeHost(state) {
//		get = (*Client).GetHostHealth
//		kind = "host"
//	} else {
//		get = (*Client).GetVirtualMachineHealth
//		kind = "vm"
//
//		err = provisioning.GetInheritedParentComputeState(ctx, req.ComputeReference, state)
//		if err != nil {
//			glog.Errorf("Error getting parent ComputeState: %s", err)
//			op.Fail(err)
//			return
//		}
//	}
//
//	client, err := NewClient(state)
//	if err != nil {
//		glog.Errorf("Error creating client: %s", err)
//		op.Fail(err)
//		return
//	}
//
//	defer client.Cleanup()
//
//	health := &provisioning.ComputeHealthResponse{}
//	err = get(client, health)
//
//	if err != nil {
//		glog.Errorf("Error getting %s health: %s", kind, err)
//
//		if isErrorNotFound(err) {
//			health.HealthState = provisioning.HealthStateUnhealthy
//		} else {
//			health.HealthState = provisioning.HealthStateUnknown
//		}
//	} else {
//		health.HealthState = provisioning.HealthStateHealthy
//	}
//
//	op.SetBody(health)
//	op.Complete()
//}
