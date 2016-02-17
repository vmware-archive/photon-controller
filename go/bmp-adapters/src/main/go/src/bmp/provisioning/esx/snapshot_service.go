package esx

//import (
//	"dcp/common"
//	"dcp/host"
//	"dcp/operation"
//	"dcp/provisioning"
//	"dcp/uri"
//	"fmt"
//	"net/http"
//
//	"golang.org/x/net/context"
//
//	"github.com/golang/glog"
//)
//
//type SnapshotService struct{}
//
//func NewSnapshotService() host.Service {
//	return host.NewServiceContext(&SnapshotService{})
//}
//
//func (s *SnapshotService) GetState() interface{} {
//	return &common.ServiceDocument{}
//}
//
//func (s *SnapshotService) HandlePatch(ctx context.Context, op *operation.Operation) {
//	req := &provisioning.ComputeSnapshotRequest{}
//	err := op.DecodeBody(req)
//	if err != nil {
//		op.Fail(err)
//		return
//	}
//
//	op.SetStatusCode(http.StatusCreated)
//	op.Complete()
//
//	ts := provisioning.NewTaskStateWrapper(ctx, req.SnapshotTaskReference, provisioning.NewComputeSubTaskState())
//	ts.PatchStage(common.TaskStageStarted)
//
//	if req.IsMockRequest {
//		// Simply PATCH task to completion and short-circuit the workflow.
//		ts.PatchStage(common.TaskStageFinished)
//		return
//	}
//
//	snapshotState := &provisioning.SnapshotState{}
//	err = provisioning.GetSnapshotState(ctx, req.SnapshotReference, snapshotState)
//	if err != nil {
//		ts.PatchFailure("Error validating SnapshotState", err)
//		return
//	}
//	computeURL := uri.Extend(uri.Local(), snapshotState.ComputeLink)
//	state := &provisioning.ComputeStateWithDescription{}
//	err = provisioning.GetChildComputeStateWithDescription(ctx, computeURL, state)
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
//	err = client.CreateSnapshot(snapshotState.Name, snapshotState.Description, snapshotState.CustomProperties)
//	if err != nil {
//		ts.PatchFailure(fmt.Sprintf("%s ID=%s", snapshotState.Name, state.ID), err)
//		return
//	}
//
//	glog.Infof("Completed snapshot request (%s) for %s", snapshotState.Name, snapshotState.ComputeLink)
//	ts.PatchStage(common.TaskStageFinished)
//}
