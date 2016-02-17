package esxcloud

import (
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"fmt"
	"net/http"

	"github.com/golang/glog"
	"golang.org/x/net/context"
)

type EnumerationService struct{}

func NewEnumerationService() host.Service {
	return host.NewServiceContext(&EnumerationService{})
}

func (s *EnumerationService) GetState() interface{} {
	return &common.ServiceDocument{}
}

func (s *EnumerationService) HandlePatch(ctx context.Context, op *operation.Operation) {
	req := &provisioning.ComputeEnumerateRequest{}
	err := op.DecodeBody(req)
	if err != nil {
		op.Fail(err)
		return
	}
	op.SetStatusCode(http.StatusCreated)
	op.Complete()

	ts := provisioning.NewTaskStateWrapper(ctx, req.EnumerationTaskReference,
		provisioning.NewResourceEnumerationTaskState())

	ts.PatchStage(common.TaskStageStarted)

	description := &provisioning.ComputeDescription{}
	descriptionURL := uri.Extend(uri.Local(), req.ComputeDescriptionLink)

	err = provisioning.GetComputeDescription(ctx, descriptionURL, description)
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("Error getting ComputeState %s", descriptionURL), err)
		return
	}

	// Get the ResourcePool
	resourcePool := &provisioning.ResourcePoolState{}
	rpoolurl := uri.Extend(uri.Local(), req.ResourcePoolLink)
	if err = provisioning.GetResourcePoolState(ctx, resourcePool, rpoolurl); err != nil {
		ts.PatchFailure(fmt.Sprintf("Error getting resource pool %s", rpoolurl), err)
		return
	}

	// get an ESX Cloud client
	var client *Client
	client, err = NewClient()
	if err != nil {
		ts.PatchFailure("Error creating ESX Cloud client", err)
		return
	}

	// List operations should be quick.  Use the default operation timeout.
	ctx, cancel := context.WithTimeout(context.Background(), operation.DefaultTimeout)
	defer cancel()

	glog.Infof("Getting instances for %s", req.EnumerationTaskReference)
	instances, err := client.ListComputeHosts(ctx, resourcePool, description, req.AdapterManagementReference)
	if err != nil {
		ts.PatchFailure("Error getting instances", err)
		return
	}

	err = provisioning.PostComputeState(ctx, instances)
	if err != nil {
		ts.PatchFailure("Error POSTing instances", err)
		return
	}

	glog.Infof("Done. Found %d instances", len(instances))
	ts.PatchStage(common.TaskStageFinished)
}
