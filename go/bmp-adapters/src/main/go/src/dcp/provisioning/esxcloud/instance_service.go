package esxcloud

import (
	"dcp"
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

type InstanceService struct{}

func NewInstanceService() host.Service {
	return host.NewServiceContext(&InstanceService{})
}

func (s *InstanceService) GetState() interface{} {
	return &common.ServiceDocument{}
}

func (s *InstanceService) HandlePatch(ctx context.Context, op *operation.Operation) {

	// Decode the request Body
	req := &provisioning.ComputeInstanceRequest{}
	err := op.DecodeBody(req)
	if err != nil {
		glog.Errorf("Error decoding ComputeInstanceRequest: %s", err)
		op.Fail(err)
		return
	}

	op.SetStatusCode(http.StatusCreated)
	op.Complete()

	ts := provisioning.NewTaskStateWrapper(ctx, req.ProvisioningTaskReference, provisioning.NewComputeSubTaskState())
	ts.PatchStage(common.TaskStageStarted)

	if req.IsMockRequest {
		provisioning.HandleMockRequest(ctx, req, ts)
		return
	}

	// Get the ComputeState
	computeHost := &provisioning.ComputeStateWithDescription{}
	err = provisioning.GetChildComputeStateWithDescription(ctx, req.ComputeReference, computeHost)

	if err != nil {
		ts.PatchFailure(fmt.Sprintf("Error getting ComputeState %s", req.ComputeReference), err)
		return
	}

	// Get the ResourcePool
	resourcePool := &provisioning.ResourcePoolState{}
	rpooluri := uri.Extend(uri.Local(), computeHost.ResourcePoolLink)
	if err = provisioning.GetResourcePoolState(ctx, resourcePool, rpooluri); err != nil {
		ts.PatchFailure(fmt.Sprintf("Error getting resource pool %s", rpooluri), err)
		return
	}

	var instance *Instance

	instance, err = NewInstance(resourcePool, computeHost)
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("Error creating instance %s", instance.Name()), err)
		return
	}

	// set a timeout for instance operations
	ctx, cancel := context.WithTimeout(context.Background(), InstanceTimeout)
	defer cancel()

	switch req.RequestType {
	case provisioning.InstanceRequestTypeCreate:
		err = s.create(ctx, instance, computeHost.ResourcePoolLink, req.ComputeReference)
	case provisioning.InstanceRequestTypeDelete:
		err = s.delete(ctx, instance, computeHost)
	default:
		ts.PatchFailure(fmt.Sprintf("%s error %s", req.ProvisioningTaskReference.String(), req.RequestType), provisioning.ErrUnsupportedRequestType)
		return
	}

	if err != nil {
		ts.PatchFailure(fmt.Sprintf("%s(%s) error", req.RequestType, instance.Name()), err)
		return
	}

	ts.PatchStage(common.TaskStageFinished)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| HandlePatch() %s request %s finished successfully.", req.RequestType, req.ComputeReference)
	}
}

// Create AND POWER-ON the instance
func (s *InstanceService) create(ctx context.Context, instance *Instance,
	resourcePoolLink string,
	computeHostStateRef uri.URI) error {
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| InstanceService:create(instance=%+v)...", instance)
	}

	diskStates, err := provisioning.GetDiskState(ctx, instance.computeHost.DiskLinks)
	if err != nil {
		return err
	}

	instance.diskStates = diskStates

	// Do the actual Insert (create VM)
	if err := instance.Insert(ctx); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| InstanceService:create(instance=%+v)...  Insert() returning error=%q", instance, err)
		}
		return err
	}

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| InstanceService:create(instance=%+v) Insert() succeeded...  Internally invoking instance.GetState(ctx)", instance)
	}

	// START VM and wait for it to start
	if err := instance.PowerOn(ctx); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| InstanceService:create(instance=%+v)...  PowerOn error=%q", instance, err)
		}
		return err
	}

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| InstanceService:create(instance=%+v) PowerOn() succeeded...  Internally invoking instance.GetState(ctx)", instance)
	}

	ipAddress, diskStates, err := instance.GetState(ctx)
	if err != nil {
		return err
	}

	// Post each disk to the PDS, then attach them to the compute host.
	diskLinks := []string{}
	for _, diskState := range diskStates {
		diskState.ResourcePoolLink = resourcePoolLink
		p, err := provisioning.PostDiskState(ctx, diskState)
		if err != nil {
			return fmt.Errorf("error posting disk state %s - %s", diskState.Name, err)
		}
		diskLinks = append(diskLinks, p.SelfLink)
	}
	chsPatchBody := &provisioning.ComputeState{
		DiskLinks:  diskLinks,
		Address:    ipAddress,
		PowerState: provisioning.PowerStateOn,
	}

	// keep the ESX CLOUD name we derived in the compute state
	chsPatchBody.CustomProperties = make(map[string]string)
	chsPatchBody.CustomProperties[customPropertyIndexVMID] = instance.esxcloudVMID

	err = provisioning.PatchComputeState(ctx, computeHostStateRef, chsPatchBody)
	if err != nil {
		return fmt.Errorf("error patching compute host %s", err)
	}
	return nil
}

// POWER-OFF and delete the instance
func (s *InstanceService) delete(ctx context.Context,
	instance *Instance,
	c *provisioning.ComputeStateWithDescription) error {

	// Step 1: PowerOff the VM (failure is ok)
	if err := instance.PowerOff(ctx); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| InstanceService:delete(instance=%+v)...  PowerOff error=%q. Ignoring and attempting delete", instance, err)
		}

	}

	// Step 2: Delete it
	if err := instance.Delete(ctx); err != nil {
		if glog.V(dcp.Debug) {
			glog.Infof("|ESXCLOUD-DEBUG| InstanceService:delete(instance=%+v)...  deleteVM error=%q. returning failure", instance, err)
		}
		return err
	}

	return provisioning.DeleteComputeState(ctx, &c.ComputeState)
}
