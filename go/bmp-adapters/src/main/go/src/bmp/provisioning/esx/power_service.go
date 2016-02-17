package esx

//import (
//	"dcp/common"
//	"dcp/host"
//	"dcp/operation"
//	"dcp/provisioning"
//	"fmt"
//	"net/http"
//
//	"golang.org/x/net/context"
//
//	"github.com/golang/glog"
//	"github.com/vmware/govmomi"
//)
//
//type PowerService struct{}
//
//func NewPowerService() host.Service {
//	return host.NewServiceContext(&PowerService{})
//}
//
//func (s *PowerService) GetState() interface{} {
//	return &common.ServiceDocument{}
//}
//
//func (s *PowerService) HandlePatch(ctx context.Context, op *operation.Operation) {
//	req := &provisioning.ComputePowerRequest{}
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
//	if ps == req.PowerState {
//		ts.PatchStage(common.TaskStageFinished)
//		return
//	}
//
//	var powerOp func() (*govmomi.Task, error)
//	shutdownGuest := false
//
//	switch req.PowerState {
//	case provisioning.PowerStateOn:
//		powerOp = vm.PowerOn
//	case provisioning.PowerStateSuspend:
//		powerOp = vm.Suspend
//	case provisioning.PowerStateOff:
//		switch req.PowerTransition {
//		case provisioning.PowerTransitionSoft:
//			// Check if VMware Tools is running and if so, shutdown the guest.
//			if toolsRunning, err := vm.IsToolsRunning(); err == nil && toolsRunning {
//				shutdownGuest = true
//			} else {
//				glog.Warning("VMware Tools not detected/running on the guest OS, will fallback to hard power off")
//				powerOp = vm.PowerOff
//			}
//		default:
//			powerOp = vm.PowerOff
//		}
//	default:
//		err = fmt.Errorf("unknown power state: %s", req.PowerState)
//		ts.PatchFailure("Error in PowerState:", err)
//		return
//	}
//
//	// TODO: PATCH progress
//	if shutdownGuest {
//		err = vm.ShutdownGuest()
//		if err != nil {
//			ts.PatchFailure("Error patching power op", err)
//			return
//		}
//
//		// Since vm.ShutdownGuest returns immediately and does not return a task, we need to wait for the power state
//		// to change on the vm.
//		err = vm.WaitForPowerOffState()
//		if err != nil {
//			ts.PatchFailure("Error waiting for power op", err)
//			return
//		}
//	} else {
//		task, err := powerOp()
//		if err != nil {
//			ts.PatchFailure("Error patching power op", err)
//			return
//		}
//
//		err = task.Wait()
//		if err != nil {
//			ts.PatchFailure("Error waiting for power op", err)
//			return
//		}
//	}
//
//	patchBody := &provisioning.ComputeState{}
//	patchBody.ID = state.ID
//	patchBody.PowerState = req.PowerState
//	err = provisioning.PatchComputeState(ctx, req.ComputeReference, patchBody)
//	if err != nil {
//		ts.PatchFailure("Error patching compute host state", err)
//		return
//	}
//
//	glog.Infof("Completed power request for %s", req.ComputeReference)
//	ts.PatchStage(common.TaskStageFinished)
//}
