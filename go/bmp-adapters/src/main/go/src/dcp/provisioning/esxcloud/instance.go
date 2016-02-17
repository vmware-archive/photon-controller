package esxcloud

import (
	"dcp"
	"dcp/provisioning"
	"dcp/provisioning/image"
	"fmt"
	"net/url"
	"path"
	"regexp"
	"strings"
	"time"

	"github.com/golang/glog"
	"golang.org/x/net/context"
)

const (
	nameKey                 = "photon-"
	InstanceTimeout         = time.Minute * 15
	customPropertyIndexVMID = "ESXCLOUD_VMID"
)

// This describes an Instance (either one to be created, or one
// that is being enumerated, deleted etc)
type Instance struct {
	// computeHost is the Guest - TODO: rename this for clarity.
	// Note that computeHost.Name is mapped to computeHost.ID via nameKey prefix
	computeHost  *provisioning.ComputeStateWithDescription
	pool         *provisioning.ResourcePoolState
	diskStates   []*provisioning.DiskState
	esxcloudVMID string
	MachineType  string // This means ESX Cloud Compute Flavor
	client       *Client
}

// NewInstance creates an Instance struct.
func NewInstance(pool *provisioning.ResourcePoolState,
	c *provisioning.ComputeStateWithDescription) (*Instance, error) {

	g, err := NewClient()
	if err != nil {
		return nil, fmt.Errorf("error creating ESX Cloud client: %s", err)
	}

	i := newInstance(pool, c, g)
	return i, nil
}

func newInstance(pool *provisioning.ResourcePoolState,
	computeHost *provisioning.ComputeStateWithDescription,
	client *Client) *Instance {

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| newInstance(pool,computeState,CLIENT) invoked")
		glog.Infof("|ESXCLOUD-TRACE| pool = %+v", pool)
		glog.Infof("|ESXCLOUD-TRACE| computeHost = %+v", computeHost)
		glog.Infof("|ESXCLOUD-TRACE| client = %+v", client)
	}

	re := regexp.MustCompile("[^/]*$") // Used below to extract last part of url

	i := &Instance{
		computeHost: computeHost,
		pool:        pool,
		MachineType: re.FindString(computeHost.DescriptionLink),
		client:      client,
	}
	if computeHost.CustomProperties != nil {
		i.esxcloudVMID = computeHost.CustomProperties[customPropertyIndexVMID]
	}
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| NewInstance(pool,computeState,CLIENT) returns %+v", i)
	}

	return i
}

// The instance name in some systems must start with a letter and can't include
// slashes.  In these cases, adpaters encode the instance name using the
// ComputeState id. Note that this is undone later in NameToComputeID().
// This isn't needed for ESX Cliud, but doing it just because it is easier for
// users to know the IaaS object were probably made via ..
// e.g. name = photon-xxx-xxxx-xxx-xxx
func (instance *Instance) Name() string {
	newName := nameKey + instance.computeHost.ID
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| Instance.Name() returns %+v", newName)
	}

	return newName
}

func (instance *Instance) Description() string {
	return instance.computeHost.DescriptionLink
}

func (instance *Instance) Project() string {
	return instance.pool.Name
}

func (instance *Instance) Zone() string {
	return instance.computeHost.Description.ZoneID
}

func (instance *Instance) APIPrefix() url.URL {
	api, _ := url.Parse(instance.computeHost.AdapterManagementReference)
	api.Path = path.Join(api.Path, instance.Project())
	return *api
}

func (instance *Instance) MachineTypeURL() *url.URL {
	u := instance.APIPrefix()
	u.Path = path.Join(u.Path, "zones", instance.Zone(), "machineTypes", instance.MachineType)
	return &u
}

func (instance *Instance) ToESXCLOUDInstance(ctx context.Context) (*EsxcapiInstance, error) {
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| ToESXCLOUDInstance() invoked")
	}

	var index int
	var disks []*EsxcapiAttachedDisk
	var isoFiles []string

	for _, ds := range instance.diskStates {
		if ds.DiskType == "CDROM" {
			// Generate the ISO file for the disk
			isoFile, err := image.Download(ctx, &instance.computeHost.ComputeState, ds)
			if err != nil {
				if glog.V(dcp.Debug) {
					glog.Infof("|ESXCLOUD-DEBUG| Failed to generate ISO for the disk: %s. Error: %q", ds.Name, err)
				}
				return nil, err
			}

			isoFiles = append(isoFiles, isoFile)
		} else {
			disk := &EsxcapiAttachedDisk{
				BootDisk:           false,
				Kind:               "ephemeral-disk",
				DiskFlavor:         ds.Name,
				DiskCapacityGBytes: ds.CapacityMBytes >> 10,
				DiskName:           ds.Name,
				SourceImageID:      ds.SourceImageReference,
			}

			if index == 0 {
				disk.BootDisk = true
			}
			index++
			disks = append(disks, disk)
		}
	}

	i := &EsxcapiInstance{
		// For GCE, Name must match '(?:[a-z](?:[-a-z0-9]{0,61}[a-z0-9])?)' so there is shennanighans with prefixing with nameKey. We could remove this prefix for ESX Cloud if we want
		Name:          instance.Name(),
		EsxcloudID:    "", // Will be set during insert
		Description:   instance.Description(),
		MachineFlavor: instance.MachineType,
		Disks:         disks,
		ISOFiles:      isoFiles,
	}

	if glog.V(dcp.Debug) {
		glog.Infof("|ESXCLOUD-DEBUG| ToESXCLOUDInstance() makes: %+v", i)
	}

	return i, nil
}

func (instance *Instance) ToComputeState(esxcloudInstance *EsxcapiInstance) *provisioning.ComputeState {

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| ToComputeState()")
	}

	return &provisioning.ComputeState{
		ID:               NameToComputeID(esxcloudInstance), // This is the photon ID
		ResourcePoolLink: instance.pool.SelfLink,
		DescriptionLink:  DescriptionToLink(esxcloudInstance),
	}
}

func (instance *Instance) Insert(ctx context.Context) error {
	esxInstance, err := instance.ToESXCLOUDInstance(ctx)
	if err != nil {
		return err
	}

	gop, err := instance.client.InsertInstance(ctx, instance.Project(), instance.Zone(), esxInstance)
	if err != nil {
		return err
	}

	instance.esxcloudVMID = gop.ID

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| InsertInstance() response: %#v", gop)
	}
	return err
}

func (instance *Instance) Delete(ctx context.Context) error {
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| DeleteInstance(instance=%+v)", instance)
	}
	gop, err := instance.client.DeleteInstance(ctx, instance.Project(), instance.Zone(), instance.esxcloudVMID)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| DeleteInstance(...) response: %#v", gop)
	}

	return err
}

func (instance *Instance) PowerOn(ctx context.Context) error {
	gop, err := instance.client.PowerOnInstance(ctx, instance.Project(), instance.Zone(), instance.esxcloudVMID)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| PowerOnInstance(...) response: %#v", gop)
	}

	return err
}

func (instance *Instance) PowerOff(ctx context.Context) error {
	gop, err := instance.client.PowerOffInstance(ctx, instance.Project(), instance.Zone(), instance.esxcloudVMID)
	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| PowerOffInstance(...) response: %#v", gop)
	}

	return err
}

// GetState returns the IP and the PersistentDiskStates
// We use instance.esxcloudVMID to know which VM to get info on
func (instance *Instance) GetState(ctx context.Context) (string,
	[]*provisioning.DiskState, error) {

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| GetState() invoked")
	}

	// Get the instance's parameters
	ci, err := instance.client.GetInstance(ctx, instance.Project(), instance.Zone(), instance.esxcloudVMID)
	if err != nil {
		return "", nil, fmt.Errorf("error querying instance - %s", err)
	}

	// First, get disk info
	diskStates := attachedDisksToDisks(ci.Disks)

	// Next get network info
	ipAddress := ci.IPAddress

	if glog.V(dcp.Debug) {
		glog.Infof("|ESXCLOUD-DEBUG| GetState().. ci.name=%q, IPAddress=%q", ci.Name, ci.IPAddress)
	}

	return ipAddress, diskStates, nil
}

func NameToComputeID(esxcloudInstance *EsxcapiInstance) string {
	return strings.TrimPrefix(esxcloudInstance.Name, nameKey)
}

func DescriptionToLink(esxcloudInstance *EsxcapiInstance) string {
	return esxcloudInstance.Description
}

func attachedDisksToDisks(disks []*EsxcapiAttachedDisk) []*provisioning.DiskState {

	diskStates := []*provisioning.DiskState{}
	for _, disk := range disks {
		// Create an Photon Disk resource based on the ESX Cloud Disk info
		diskState := &provisioning.DiskState{
			ID:                   disk.ID,
			ZoneID:               "", // ESX Cloud disks don't have zones
			SourceImageReference: disk.SourceImageID,

			// compute.Disk.Type is a URI to a type description. Default this to HDD for now.
			DiskType:   provisioning.DiskTypeHDD,
			Name:       disk.DiskName,
			DiskStatus: provisioning.DiskStatusAttached,

			// compute.Disk.SizeGb is is GB (not Gb).
			CapacityMBytes: disk.DiskCapacityGBytes * 1024,
		}

		diskStates = append(diskStates, diskState)
	}

	if glog.V(dcp.Trace) {
		glog.Infof("|ESXCLOUD-TRACE| attachedDisksToDisks() returns %+v", diskStates)
	}

	return diskStates
}
