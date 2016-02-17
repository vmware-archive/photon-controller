package esx

//import (
//	"dcp"
//	"dcp/common"
//	"dcp/host"
//	"dcp/operation"
//	"dcp/provisioning"
//	"dcp/uri"
//	"encoding/json"
//	"fmt"
//	"net/http"
//	"sync"
//	"time"
//
//	"golang.org/x/net/context"
//
//	"github.com/golang/glog"
//	"github.com/vmware/govmomi"
//	"github.com/vmware/govmomi/vim25/mo"
//	"github.com/vmware/govmomi/vim25/types"
//)
//
//// Enumeration service will retrieve all VMs from specified target compute and post
//// them back to photon
//type EnumerationService struct {
//	serviceContext *host.ServiceContext
//}
//
//// Constant for VM and host property name, path and others
//const (
//	VirtualMachineType = "VirtualMachine"
//	VMNameProp         = "config.name"
//	VMInstanceUUIDProp = "config.instanceUuid"
//	VMNumCPUProp       = "summary.config.numCpu"
//	VMExtraConfigProp  = "config.extraConfig"
//	VMPowerStateProp   = "runtime.powerState"
//	VMMaxCPUProp       = "runtime.maxCpuUsage"
//	VMMaxMemProp       = "runtime.maxMemoryUsage"
//	VMPropPath         = "vm"
//
//	HostType                = "HostSystem"
//	HostMemorySizeBytesProp = "summary.hardware.memorySize"
//	HostCPUMhtzPerProp      = "summary.hardware.cpuMhz"
//	HostNumberCPUProp       = "summary.hardware.numCpuCores"
//	HostName                = "name"
//
//	Megabytes              int64         = 1024 * 1024
//	InitSleepTimeOnFailure time.Duration = 5
//	TimeoutOnFailure       time.Duration = 120
//)
//
//func NewEnumerationService() host.Service {
//	enumService := &EnumerationService{}
//	enumService.serviceContext = host.NewServiceContext(enumService)
//	return enumService.serviceContext
//}
//
//func (s *EnumerationService) GetState() interface{} {
//	return &common.ServiceDocument{}
//}
//
//func (s *EnumerationService) HandlePatch(ctx context.Context, op *operation.Operation) {
//	req := &provisioning.ComputeEnumerateRequest{}
//	if err := op.DecodeBody(req); err != nil {
//		op.Fail(err)
//		return
//	}
//
//	if err := validateInputRequest(ctx, req); err != nil {
//		op.Fail(err)
//		return
//	}
//
//	var ts *provisioning.TaskStateWrapper
//	if req.EnumerationTaskReference.URL != nil {
//		ts = provisioning.NewTaskStateWrapper(ctx, req.EnumerationTaskReference,
//			provisioning.NewResourceEnumerationTaskState())
//
//		ts.PatchStage(common.TaskStageStarted)
//	}
//
//	hostState := &provisioning.ComputeStateWithDescription{}
//	parentComputeURI := uri.Extend(uri.Local(), req.ParentComputeLink)
//	if err := provisioning.GetComputeStateWithDescription(ctx, parentComputeURI, hostState); err != nil {
//		logError(ts, fmt.Sprintf("Error retrieving compute with description for %s", parentComputeURI), err)
//		return
//	}
//
//	op.SetStatusCode(http.StatusCreated)
//	op.Complete()
//
//	hostEnumKey := getHostEnumKey(hostState)
//
//	switch req.EnumerationAction {
//	case provisioning.EnumerationActionStart:
//		if !esxHostEnumMap.put(hostEnumKey) {
//			glog.V(dcp.Debug).Infof("Enumeration service has already been started for %v", hostEnumKey)
//			return
//		}
//		glog.V(dcp.Debug).Infof("Launching enumeration service for %v", hostEnumKey)
//	case provisioning.EnumerationActionStop:
//		// Just remove it from map, property collector will exit in next loop
//		if esxHostEnumMap.remove(getHostEnumKey(hostState)) {
//			glog.V(dcp.Debug).Infof("Enumeration service will be stopped for %v", hostEnumKey)
//		} else {
//			glog.V(dcp.Debug).Infof("Enumeration service is not running or has already been stopped for %v", hostEnumKey)
//		}
//		return
//	case provisioning.EnumerationActionRefresh:
//		glog.V(dcp.Debug).Infof("Running enumeration service in refresh mode for %v", hostEnumKey)
//	default:
//
//	}
//
//	vimClient, err := NewClient(hostState)
//	if err != nil {
//		logError(ts, fmt.Sprintf("Error creating client for %+v", hostState), err)
//		return
//	}
//
//	defer vimClient.Cleanup()
//
//	pc, err := vimClient.Client.NewPropertyCollector()
//	if err != nil {
//		logError(ts, fmt.Sprintf("Get property collector failed for %s", hostEnumKey), err)
//		return
//	}
//
//	defer pc.Destroy()
//
//	hostSystem, err := vimClient.finder.DefaultHostSystem()
//	if err != nil {
//		return
//	}
//
//	glog.V(dcp.Debug).Infof("Start creating filter for %v", hostEnumKey)
//	filterSpec := createFilterSpec(hostSystem)
//	err = pc.CreateFilter(*filterSpec)
//	if err != nil {
//		logError(ts, fmt.Sprintf("Error create property collector filter for %v", hostEnumKey), err)
//		return
//	}
//
//	glog.V(dcp.Debug).Infof("Start waiting for inventory change for %v", hostEnumKey)
//	sleepTime := InitSleepTimeOnFailure
//	for version := ""; ; {
//		res, err := pc.WaitForUpdates(version)
//		if err != nil {
//			logError(ts, fmt.Sprintf("Got an error while waiting for update from %v", hostEnumKey), err)
//			// stop listening, the call is responsible to re-start it if needed.
//			esxHostEnumMap.remove(getHostEnumKey(hostState))
//			break
//		} else if res != nil {
//			fs := res.FilterSet
//			if glog.V(dcp.Trace) {
//				jsonObj, err := json.MarshalIndent(fs, "", "  ")
//				if err == nil {
//					glog.Infof("Got update set for %v, detail: %+v", hostEnumKey, string(jsonObj))
//				} else {
//					glog.Infof("Got update set for %v, detail: %+v", hostEnumKey, fs)
//				}
//			}
//
//			vms, err := vimClient.getVirtualMachineProperties(ctx, []string{VMInstanceUUIDProp, VMExtraConfigProp})
//
//			if err != nil {
//				glog.Errorf("Error occurred retreiving vm's auilary informatoin for %v", hostEnumKey)
//				time.Sleep(sleepTime * time.Second)
//				sleepTime = sleepTime * 2
//				continue
//			}
//			glog.V(dcp.Trace).Infof("Got vms properties for %v, details: %+v", hostEnumKey, vms)
//
//			// First sync, delete compute for orphaned VMs
//			if len(version) == 0 {
//				s.deleteOrphanedVMsOnInitialSync(ctx, hostState, vms)
//			}
//
//			err = s.processChangeSet(ctx, hostState, fs, vms)
//			if err == nil {
//				version = res.Version
//				sleepTime = InitSleepTimeOnFailure
//			} else {
//				glog.V(dcp.Debug).Infof("Error occurred while processing changes for %s, detail error %+v.", hostEnumKey, err)
//				if sleepTime > TimeoutOnFailure {
//					esxHostEnumMap.remove(getHostEnumKey(hostState))
//					glog.V(dcp.Debug).Infof("Can't connect to %v after %v seconds, stop listening.", hostEnumKey, sleepTime)
//				} else {
//					time.Sleep(sleepTime * time.Second)
//					sleepTime = sleepTime * 2
//				}
//			}
//			glog.V(dcp.Trace).Infof("Get new version for %v as %+v", hostEnumKey, res.Version)
//		} else {
//			glog.V(dcp.Trace).Infof("No update, time out from %v, will continue monitor.", hostEnumKey)
//		}
//		if !esxHostEnumMap.get(getHostEnumKey(hostState)) {
//			break
//		}
//	}
//
//	if ts != nil {
//		ts.PatchStage(common.TaskStageFinished)
//	}
//}
//
//// Validate input, retrieve the parent compute link if resource pool and adapter management refrence is specififed
//func validateInputRequest(ctx context.Context, req *provisioning.ComputeEnumerateRequest) error {
//	if len(req.EnumerationAction) == 0 {
//		req.EnumerationAction = provisioning.EnumerationActionRefresh
//	}
//
//	if len(req.ParentComputeLink) == 0 {
//		queryString := "(%s eq '%s' and %s eq '%s' and %s eq '%s')"
//		queryString = fmt.Sprintf(queryString, provisioning.FieldNameResourcePoolLink, req.ResourcePoolLink,
//			provisioning.FieldNameAdapterManagementReference, req.AdapterManagementReference,
//			common.FieldNameDocumentKind, provisioning.ComputeStateDocumentKind)
//		glog.V(dcp.Debug).Infof("Sending filter to photon to query host %s", queryString)
//		results := provisioning.ExecuteODataQuery(ctx, queryString)
//		if results == nil || results.DocumentLinks == nil || len(results.DocumentLinks) != 1 {
//			return fmt.Errorf("Can't retrieve host link from resource pool %s and adapter refrence %s",
//				req.ResourcePoolLink, req.AdapterManagementReference)
//		}
//		req.ParentComputeLink = results.DocumentLinks[0]
//	}
//	return nil
//}
//
//// Create a filter spec for property listener
//func createFilterSpec(hostSystem *govmomi.HostSystem) *types.CreateFilter {
//	ospec := types.ObjectSpec{
//		Obj: hostSystem.Reference(),
//		SelectSet: []types.BaseSelectionSpec{
//			&types.TraversalSpec{
//				Type: HostType,
//				Path: VMPropPath,
//				Skip: false,
//			},
//		},
//		Skip: false,
//	}
//
//	vmSpec := types.PropertySpec{
//		Type:    VirtualMachineType,
//		PathSet: []string{VMNameProp, VMPowerStateProp, VMMaxCPUProp, VMMaxMemProp, VMInstanceUUIDProp, VMNumCPUProp},
//	}
//
//	hostSpec := types.PropertySpec{
//		Type:    HostType,
//		PathSet: []string{HostMemorySizeBytesProp, HostCPUMhtzPerProp, HostNumberCPUProp, HostName},
//	}
//
//	filterSpec := types.CreateFilter{
//		Spec: types.PropertyFilterSpec{
//			ObjectSet: []types.ObjectSpec{ospec},
//			PropSet:   []types.PropertySpec{hostSpec, vmSpec},
//		},
//	}
//	return &filterSpec
//}
//
//// Process the change set
//func (s *EnumerationService) processChangeSet(ctx context.Context, hostState *provisioning.ComputeStateWithDescription,
//	filterUpdates []types.PropertyFilterUpdate, vms []mo.VirtualMachine) error {
//	var err error
//	for _, filterUpdate := range filterUpdates {
//		for _, objectUpdate := range filterUpdate.ObjectSet {
//			kind := objectUpdate.Kind
//			objType := objectUpdate.Obj.Type
//			switch kind {
//			case types.ObjectUpdateKindEnter:
//				if objType == VirtualMachineType {
//					err = s.processNewOrUpdateVM(ctx, hostState, &objectUpdate, vms)
//				} else if objType == HostType {
//					err = s.processHost(ctx, hostState, &objectUpdate)
//				}
//			case types.ObjectUpdateKindModify:
//				if objType == VirtualMachineType {
//					err = s.processNewOrUpdateVM(ctx, hostState, &objectUpdate, vms)
//				}
//			case types.ObjectUpdateKindLeave:
//				err = s.processDeletedVM(ctx, hostState, &objectUpdate, vms)
//			default:
//			}
//			if err != nil {
//				return err
//			}
//		}
//	}
//	return nil
//}
//
//// Process a VM Object set
//func (s *EnumerationService) processNewOrUpdateVM(ctx context.Context, hostState *provisioning.ComputeStateWithDescription,
//	objectUpdate *types.ObjectUpdate, vms []mo.VirtualMachine) error {
//
//	state := &provisioning.ComputeStateWithDescription{}
//	vmMoref := GetMorefAsString(&objectUpdate.Obj)
//	err := provisioning.GetComputeStateByID(ctx, hostState.SelfLink, GetVMMorefFieldName(), vmMoref, state)
//	if err == nil {
//		// VM already exists, will check if there is any changes and update them if needed.
//		glog.V(dcp.Trace).Infof("Found VM service %v, the service document link is %v", vmMoref, state.SelfLink)
//
//		descChanged := getVMComputeDescChange(objectUpdate, &state.Description)
//		patchComputeState := &provisioning.ComputeState{}
//		computeChanged := getVMComputeStateChange(objectUpdate, &state.ComputeState, patchComputeState)
//
//		if !descChanged && !computeChanged {
//			glog.V(dcp.Trace).Infof("No changes for VM %v on host %v, continue.", vmMoref, hostState.SelfLink)
//			return nil
//		}
//		if descChanged {
//			glog.V(dcp.Trace).Infof("VM configuration has been changed, will create a new compute description: %+v.", state.Description)
//
//			state.Description.SelfLink = ""
//			state.Description.ID = ""
//			if err := provisioning.PostComputeDescription(ctx, &state.Description); err != nil {
//				return err
//			}
//			patchComputeState.DescriptionLink = state.Description.SelfLink
//		}
//		computePatchURI := uri.Extend(uri.Local(), state.ComputeState.SelfLink)
//		return provisioning.PatchComputeState(ctx, computePatchURI, patchComputeState)
//	}
//	glog.V(dcp.Trace).Infof("VM service %v doesn't exist, start creating..", vmMoref)
//
//	// First check if VM has a description link in extra config
//	// If it doesn't, create a compute description first
//	compDescLink := retrieveExistingComputeDescLink(&objectUpdate.Obj, vms)
//	if len(compDescLink) == 0 {
//		computeDesc := &provisioning.ComputeDescription{}
//		getVMComputeDescChange(objectUpdate, computeDesc)
//
//		// fill in other information for compute desc
//		computeDesc.PowerAdapterReference = s.getPowerServiceAdapterURI()
//		computeDesc.BootAdapterReference = s.getBootServiceAdapterURI()
//		computeDesc.InstanceAdapterReference = s.getInstanceServiceAdapterURI()
//		computeDesc.EnumerationAdapterReference = hostState.Description.EnumerationAdapterReference
//		computeDesc.HealthAdapterReference = hostState.Description.HealthAdapterReference
//		computeDesc.TenantLinks = hostState.Description.TenantLinks
//
//		if err = provisioning.PostComputeDescription(ctx, computeDesc); err != nil {
//			return err
//		}
//		compDescLink = computeDesc.SelfLink
//	}
//	// TODO: Need to handle VM reconfigure case.
//
//	// Create a new compute for VM
//	compute := &provisioning.ComputeState{}
//	compute.CustomProperties = map[string]string{}
//	getVMComputeStateChange(objectUpdate, compute, compute)
//	compute.DescriptionLink = compDescLink
//	compute.ResourcePoolLink = hostState.ResourcePoolLink
//	compute.ParentLink = hostState.SelfLink
//	compute.TenantLinks = hostState.TenantLinks
//	compute.CustomProperties[provisioning.FieldNameComputeType] = provisioning.ComputeTypeVMGuest
//	compute.CustomProperties[FieldNameManagedObjectReference] = vmMoref
//	instanceID := compute.ID
//	compute.ID = ""
//
//	if err = provisioning.PostOneComputeState(ctx, compute); err != nil {
//		return err
//	}
//
//	// we have to do a patch for ID, otherwise, selfLink will take this ID
//	patchCompute := provisioning.ComputeState{}
//	patchCompute.ID = instanceID
//	patchComputeURI := uri.Extend(uri.Local(), compute.SelfLink)
//	return provisioning.PatchComputeState(ctx, patchComputeURI, &patchCompute)
//}
//
//// Process VM delete
//func (s *EnumerationService) processDeletedVM(ctx context.Context, hostState *provisioning.ComputeStateWithDescription,
//	objectUpdate *types.ObjectUpdate, vms []mo.VirtualMachine) error {
//	state := &provisioning.ComputeStateWithDescription{}
//	err := provisioning.GetComputeStateByID(ctx, hostState.SelfLink, GetVMMorefFieldName(),
//		GetMorefAsString(&objectUpdate.Obj), state)
//	if err != nil {
//		return err
//	}
//	return provisioning.DeleteComputeState(ctx, &state.ComputeState)
//}
//
//// First sync, delete compute for orphaned VMs, enumeration process will continue even with failure
//func (s *EnumerationService) deleteOrphanedVMsOnInitialSync(ctx context.Context, hostState *provisioning.ComputeStateWithDescription,
//	vms []mo.VirtualMachine) {
//	computeMap, err := provisioning.GetComputeStatesByParentLink(ctx, hostState.SelfLink)
//	if err != nil {
//		glog.Errorf("Getting compute states failed for parent host %s", hostState.SelfLink)
//		return
//	}
//
//	vmMorefMap := map[string]string{}
//	for _, vm := range vms {
//		moref := GetMorefAsString(&vm.Self)
//		vmMorefMap[moref] = moref
//	}
//
//	for _, computeState := range computeMap {
//		if computeState.CustomProperties != nil {
//			currMoref := computeState.CustomProperties[FieldNameManagedObjectReference]
//			if len(currMoref) > 0 && len(vmMorefMap[currMoref]) == 0 {
//				err = provisioning.DeleteComputeState(ctx, &computeState)
//				glog.Errorf("Deleting compute state %s on host %s failed", computeState.SelfLink, hostState.SelfLink)
//			}
//		}
//	}
//}
//
//// Update host stats
//func (s *EnumerationService) processHost(ctx context.Context, hostState *provisioning.ComputeStateWithDescription, objectUpdate *types.ObjectUpdate) error {
//	var memorySize int64
//	var cpuMhtz int32
//	var numCPU int16
//	var name string
//
//	descChanged := false
//	nameChanged := false
//
//	for _, propChange := range objectUpdate.ChangeSet {
//		if propChange.Val == nil {
//			continue
//		}
//		switch propChange.Name {
//		case HostMemorySizeBytesProp:
//			memorySize = propChange.Val.(int64)
//		case HostCPUMhtzPerProp:
//			cpuMhtz = propChange.Val.(int32)
//		case HostNumberCPUProp:
//			numCPU = propChange.Val.(int16)
//		case HostName:
//			name = propChange.Val.(string)
//		default:
//		}
//	}
//
//	if hostState.Description.CPUCount != int64(numCPU) {
//		descChanged = true
//		hostState.Description.CPUCount = int64(numCPU)
//	}
//
//	if hostState.Description.CPUMhzPerCore != int64(cpuMhtz) {
//		descChanged = true
//		hostState.Description.CPUMhzPerCore = int64(cpuMhtz)
//	}
//
//	if hostState.Description.TotalMemoryBytes != memorySize {
//		descChanged = true
//		hostState.Description.TotalMemoryBytes = memorySize
//	}
//
//	if hostState.CustomProperties == nil || len(hostState.CustomProperties[provisioning.FieldNameDisplayName]) == 0 ||
//		hostState.CustomProperties[provisioning.FieldNameDisplayName] != name {
//		nameChanged = true
//	}
//
//	if !descChanged && !nameChanged {
//		return nil
//	}
//
//	if descChanged {
//		glog.V(dcp.Trace).Infof("Host %v configuration has been changed, will create a new compute description", hostState.SelfLink)
//
//		hostState.Description.SelfLink = ""
//		hostState.Description.ID = ""
//		if err := provisioning.PostComputeDescription(ctx, &hostState.Description); err != nil {
//			return err
//		}
//	}
//
//	computePatchURI := uri.Extend(uri.Local(), hostState.ComputeState.SelfLink)
//	patchHostState := &provisioning.ComputeState{}
//	if descChanged {
//		patchHostState.DescriptionLink = hostState.Description.SelfLink
//	}
//
//	patchHostState.CustomProperties = map[string]string{}
//	if nameChanged {
//		glog.V(dcp.Trace).Infof("Host name has been changed for %+v.", hostState.SelfLink)
//		patchHostState.CustomProperties[provisioning.FieldNameDisplayName] = name
//	}
//	patchHostState.CustomProperties[FieldNameManagedObjectReference] = GetMorefAsString(&objectUpdate.Obj)
//
//	return provisioning.PatchComputeState(ctx, computePatchURI, patchHostState)
//}
//
//// Update the new compute state from ESX object update set by comparing it with current state
//// return true if any value changed
//func getVMComputeStateChange(objectUpdate *types.ObjectUpdate, currComputeState *provisioning.ComputeState,
//	newComputeState *provisioning.ComputeState) bool {
//	changed := false
//	for _, propChange := range objectUpdate.ChangeSet {
//		if propChange.Val == nil {
//			continue
//		}
//		switch propChange.Name {
//		case VMNameProp:
//			name := propChange.Val.(string)
//			if currComputeState.CustomProperties == nil ||
//				len(currComputeState.CustomProperties[provisioning.FieldNameDisplayName]) == 0 ||
//				currComputeState.CustomProperties[provisioning.FieldNameDisplayName] != name {
//				changed = true
//				if newComputeState.CustomProperties == nil {
//					newComputeState.CustomProperties = map[string]string{}
//				}
//				newComputeState.CustomProperties[provisioning.FieldNameDisplayName] = name
//			}
//
//		case VMPowerStateProp:
//			powerState := toComputePowerState(propChange.Val.(types.VirtualMachinePowerState))
//			if currComputeState.PowerState != powerState {
//				changed = true
//				newComputeState.PowerState = powerState
//			}
//		case VMInstanceUUIDProp:
//			instanceID := propChange.Val.(string)
//			if currComputeState.ID != instanceID {
//				changed = true
//				newComputeState.ID = instanceID
//			}
//		default:
//		}
//	}
//
//	return changed
//}
//
//// Update the new compute description from ESX object update set by comparing it with current description
//// return true if any value changed
//func getVMComputeDescChange(objectUpdate *types.ObjectUpdate, computeDesc *provisioning.ComputeDescription) bool {
//	var maxCPU int32
//	changed := false
//
//	for _, propChange := range objectUpdate.ChangeSet {
//		if propChange.Val == nil {
//			continue
//		}
//		switch propChange.Name {
//		case VMMaxCPUProp:
//			maxCPU = propChange.Val.(int32)
//		case VMMaxMemProp:
//			maxMemory := propChange.Val.(int32)
//			if computeDesc.TotalMemoryBytes != int64(maxMemory) {
//				changed = true
//				computeDesc.TotalMemoryBytes = int64(maxMemory)
//			}
//		case VMNumCPUProp:
//			numCPU := propChange.Val.(int32)
//			if computeDesc.CPUCount != int64(numCPU) {
//				changed = true
//				computeDesc.CPUCount = int64(numCPU)
//			}
//		default:
//		}
//	}
//	// process maxCPU at last since we need to wait for num of CPU
//	if maxCPU > 0 {
//		numCPU := computeDesc.CPUCount
//		if numCPU <= 0 {
//			numCPU = 1
//		}
//		if computeDesc.CPUMhzPerCore != int64(maxCPU)/numCPU {
//			changed = true
//			computeDesc.CPUMhzPerCore = int64(maxCPU) / numCPU
//		}
//	}
//
//	return changed
//}
//
//// Return a extra config value
//func getExtraConfig(vm *mo.VirtualMachine, key string) string {
//	if vm.Config == nil || vm.Config.ExtraConfig == nil {
//		return ""
//	}
//	for _, opt := range vm.Config.ExtraConfig {
//		val := opt.GetOptionValue()
//		if val.Key == key {
//			return val.Value.(string)
//		}
//	}
//
//	return ""
//}
//
//// Return the exist compute description link if it exists
//func retrieveExistingComputeDescLink(obj *types.ManagedObjectReference, vms []mo.VirtualMachine) string {
//	for _, vm := range vms {
//		if vm.Reference() == *obj {
//			return getExtraConfig(&vm, propDescriptionLink)
//		}
//	}
//	return ""
//}
//
//// Log a error to parent task if specified, otherwise log it to standard log
//func logError(ts *provisioning.TaskStateWrapper, message string, err error) {
//	if ts != nil {
//		ts.PatchFailure(message, err)
//	} else {
//		glog.Errorf("%s, detail: %+v", message, err)
//	}
//}
//
//// Convert a VM power state from govmomi to enatai
//func toComputePowerState(powerState types.VirtualMachinePowerState) string {
//	switch powerState {
//	case types.VirtualMachinePowerStatePoweredOn:
//		return provisioning.PowerStateOn
//	case types.VirtualMachinePowerStatePoweredOff:
//		return provisioning.PowerStateOff
//	case types.VirtualMachinePowerStateSuspended:
//		return provisioning.PowerStateSuspend
//	default:
//		return provisioning.PowerStateUnknown
//	}
//}
//
//// Return power adapter reference
//func (s *EnumerationService) getPowerServiceAdapterURI() string {
//	return s.serviceContext.Host().URI().String() + "provisioning/esx/power-service"
//}
//
//// Return boot adapter reference
//func (s *EnumerationService) getBootServiceAdapterURI() string {
//	return s.serviceContext.Host().URI().String() + "provisioning/esx/boot-service"
//}
//
//// Return instance adapter reference
//func (s *EnumerationService) getInstanceServiceAdapterURI() string {
//	return s.serviceContext.Host().URI().String() + "provisioning/esx/instance-service"
//}
//
//func getHostEnumKey(hostState *provisioning.ComputeStateWithDescription) string {
//	return "hostLink:" + hostState.SelfLink + "-adapterReference:" + hostState.AdapterManagementReference
//}
//
//// Synchronized map to keep track if an enumeration service has been started in listening mode for a host
//type syncMap struct {
//	sync.RWMutex
//	listenerMap map[string]bool
//}
//
//var esxHostEnumMap = syncMap{
//	listenerMap: make(map[string]bool),
//}
//
//func (m *syncMap) put(key string) bool {
//	m.RLock()
//	_, ok := m.listenerMap[key]
//	m.RUnlock()
//	if ok {
//		return false
//	}
//
//	m.Lock()
//	m.listenerMap[key] = true
//	m.Unlock()
//	return true
//}
//
//func (m *syncMap) remove(key string) bool {
//	m.RLock()
//	_, ok := m.listenerMap[key]
//	m.RUnlock()
//	if !ok {
//		return false
//	}
//
//	m.Lock()
//	delete(m.listenerMap, key)
//	m.Unlock()
//	return true
//}
//
//func (m *syncMap) get(key string) bool {
//	m.RLock()
//	_, ok := m.listenerMap[key]
//	m.RUnlock()
//	return ok
//}
