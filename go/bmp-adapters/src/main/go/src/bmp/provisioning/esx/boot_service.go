package esx

//import (
//	"dcp/common"
//	"dcp/host"
//	"dcp/operation"
//	"dcp/provisioning"
//	"net/http"
//	"os"
//
//	"github.com/golang/glog"
//
//	"golang.org/x/net/context"
//)
//
//var mockIP = os.Getenv("DCP_ESX_MOCK_IP")
//
//type BootService struct{}
//
//func NewBootService() host.Service {
//	return host.NewServiceContext(&BootService{})
//}
//
//func (s *BootService) GetState() interface{} {
//	return &common.ServiceDocument{}
//}
//
//func (s *BootService) HandlePatch(ctx context.Context, op *operation.Operation) {
//	req := &provisioning.ComputeBootRequest{}
//	err := op.DecodeBody(req)
//	if err != nil {
//		op.Fail(err)
//		return
//	}
//
//	op.SetStatusCode(http.StatusCreated)
//	op.Complete()
//
//	ts := provisioning.NewTaskStateWrapper(ctx, req.ProvisioningTaskReference, provisioning.NewComputeSubTaskState())
//	ts.PatchStage(common.TaskStageStarted)
//
//	if req.IsMockRequest {
//		// Simply PATCH task to completion and short-circuit the workflow.
//		ts.PatchStage(common.TaskStageFinished)
//		return
//	}
//
//	state := &provisioning.ComputeStateWithDescription{}
//	err = provisioning.GetChildComputeStateWithDescription(ctx, req.ComputeReference, state)
//	if err != nil {
//		ts.PatchFailure("Error getting ComputeState", err)
//		return
//	}
//
//	client, err := NewClient(state)
//	if err != nil {
//		ts.PatchFailure("Error creating client", err)
//		return
//	}
//
//	defer client.Cleanup()
//
//	vm, err := client.VirtualMachine()
//	if err != nil {
//		ts.PatchFailure("Error getting vm", err)
//		return
//	}
//
//	ps, err := client.PowerState(vm)
//	if err != nil {
//		ts.PatchFailure("Error getting powerstate", err)
//		return
//	}
//
//	// TODO: PATCH progress
//	if ps == provisioning.PowerStateOn {
//		task, err := vm.PowerOff()
//		if err != nil {
//			ts.PatchFailure("Error powering off", err)
//			return
//		}
//
//		err = task.Wait()
//		if err != nil {
//			ts.PatchFailure("Error in powering off task", err)
//			return
//		}
//	}
//
//	err = client.SetBootOrder(vm, req.BootDeviceOrder)
//	if err != nil {
//		ts.PatchFailure("Error setting boot order", err)
//		return
//	}
//
//	task, err := vm.PowerOn()
//	if err != nil {
//		ts.PatchFailure("Error powering on", err)
//		return
//	}
//
//	err = task.Wait()
//	if err != nil {
//		ts.PatchFailure("Error in powering on task", err)
//		return
//	}
//
//	if mockIP != "" {
//		state.Address = mockIP
//	} else {
//
//		// New context for execution of this boot request.
//		ctx, cancel := context.WithCancel(context.Background())
//		defer cancel()
//
//		state.Address, err = client.WaitForIP(ctx, vm)
//		if err != nil {
//			ts.PatchFailure("Error waiting for IP", err)
//			return
//		}
//	}
//
//	state.PowerState = provisioning.PowerStateOn
//	patchBody := &provisioning.ComputeState{}
//	patchBody.ID = state.ID
//	patchBody.Address = state.Address
//	patchBody.PowerState = state.PowerState
//	err = provisioning.PatchComputeState(ctx, req.ComputeReference, patchBody)
//	if err != nil {
//		ts.PatchFailure("Error patching compute host state", err)
//		return
//	}
//
//	glog.Infof("Completed boot request for %s", req.ComputeReference)
//	ts.PatchStage(common.TaskStageFinished)
//}
