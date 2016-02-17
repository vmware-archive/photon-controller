package esx

//import (
//	"dcp/client"
//	"dcp/common"
//	"dcp/common/test"
//	"dcp/operation"
//	"dcp/provisioning"
//	"dcp/uri"
//	"testing"
//	"time"
//
//	"golang.org/x/net/context"
//)
//
//func powerServiceWithESX(t *testing.T, state string) {
//	ctx := context.Background()
//
//	createWithESX(t, func(th *test.ServiceHost, chsURI uri.URI) {
//		if err := th.StartServiceSync(provisioning.PowerServiceEsx, NewPowerService()); err != nil {
//			t.Fatal(err)
//		}
//
//		taskState := provisioning.NewComputeSubTaskState()
//		taskState.TaskInfo.Stage = common.TaskStageCreated
//		taskURI := th.StartMock(taskState)
//		//Power-On VM before triggering Suspend/power-off
//		if state == provisioning.PowerStateSuspend || state == provisioning.PowerStateOff {
//			req := provisioning.ComputePowerRequest{
//				ComputeReference:          chsURI,
//				ProvisioningTaskReference: taskURI,
//				PowerState:                provisioning.PowerStateOn,
//			}
//
//			op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.PowerServiceEsx), nil)
//			if err := client.Send(op.SetBody(&req)).Wait(); err != nil {
//				t.Errorf("Error issuing PATCH: %s", err)
//			}
//
//			err := test.WaitForTaskCompletion(time.Now().Add(operation.DefaultTimeout), taskURI)
//			if err != nil {
//				t.Error(err)
//			}
//
//			state := &provisioning.ComputeStateWithDescription{}
//			if err := provisioning.GetComputeStateWithDescription(ctx, chsURI, state); err != nil {
//				t.Error(err)
//			}
//		}
//		req := provisioning.ComputePowerRequest{
//			ComputeReference:          chsURI,
//			ProvisioningTaskReference: taskURI,
//			PowerState:                state,
//		}
//
//		op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.PowerServiceEsx), nil)
//		if err := client.Send(op.SetBody(&req)).Wait(); err != nil {
//			t.Errorf("Error issuing PATCH: %s", err)
//		}
//
//		err := test.WaitForTaskCompletion(time.Now().Add(operation.DefaultTimeout), taskURI)
//		if err != nil {
//			t.Error(err)
//		}
//
//		state := &provisioning.ComputeStateWithDescription{}
//		if err := provisioning.GetComputeStateWithDescription(ctx, chsURI, state); err != nil {
//			t.Error(err)
//		}
//
//		if state.PowerState != req.PowerState {
//			t.Errorf("ComputeState was not PATCHed with PowerState=%s (%s)", req.PowerState, state.PowerState)
//		}
//	})
//}
//
//func TestPowerOnServiceWithESX(t *testing.T) {
//	powerServiceWithESX(t, provisioning.PowerStateOn)
//}
//
//func TestPowerOffServiceWithESX(t *testing.T) {
//	powerServiceWithESX(t, provisioning.PowerStateOff)
//}
//
//func TestPowerSuspendServiceWithESX(t *testing.T) {
//	powerServiceWithESX(t, provisioning.PowerStateSuspend)
//}
