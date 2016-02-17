package esxcloud

import (
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"net/http"

	"golang.org/x/net/context"
)

type BootService struct{}

func NewBootService() host.Service {
	return host.NewServiceContext(&BootService{})
}

func (s *BootService) GetState() interface{} {
	return &common.ServiceDocument{}
}

func (s *BootService) HandlePatch(ctx context.Context, op *operation.Operation) {
	req := &provisioning.ComputeBootRequest{}
	err := op.DecodeBody(req)
	if err != nil {
		op.Fail(err)
		return
	}
	// no-op for now; instance service for esxcloud powers on the object currently
	op.SetStatusCode(http.StatusCreated)
	op.Complete()

	ts := provisioning.NewTaskStateWrapper(ctx, req.ProvisioningTaskReference, provisioning.NewComputeSubTaskState())
	ts.PatchStage(common.TaskStageFinished)
	return
}
