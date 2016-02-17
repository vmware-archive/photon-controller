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
//)
//
//type InstanceService struct{}
//
//func NewInstanceService() host.Service {
//	return host.NewServiceContext(&InstanceService{})
//}
//
//func (s *InstanceService) GetState() interface{} {
//	return &common.ServiceDocument{}
//}
//
//func (s *InstanceService) HandlePatch(ctx context.Context, op *operation.Operation) {
//	req := &provisioning.ComputeInstanceRequest{}
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
//		provisioning.HandleMockRequest(ctx, req, ts)
//		return
//	}
//
//	state := &provisioning.ComputeStateWithDescription{}
//	err = provisioning.GetChildComputeStateWithDescription(ctx, req.ComputeReference, state)
//	if err != nil {
//		ts.PatchFailure("Error validating ComputeState", err)
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
//	switch req.RequestType {
//	case provisioning.InstanceRequestTypeCreate:
//		err = client.CreateInstance(ctx, req.ComputeReference)
//	case provisioning.InstanceRequestTypeDelete:
//		err = client.DeleteInstance(ctx)
//	default:
//		err = provisioning.ErrUnsupportedRequestType
//		return
//	}
//
//	if err != nil {
//		ts.PatchFailure(fmt.Sprintf("%s ID=%s", req.RequestType, state.ID), err)
//		return
//	}
//
//	glog.Infof("Completed instance request (%s) for %s", req.RequestType, req.ComputeReference)
//	ts.PatchStage(common.TaskStageFinished)
//}
