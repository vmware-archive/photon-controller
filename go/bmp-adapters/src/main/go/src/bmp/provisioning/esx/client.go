package esx

//import (
//	"dcp/provisioning"
//	"dcp/provisioning/image"
//	"dcp/uri"
//	"fmt"
//	"net"
//	"net/http"
//	"net/url"
//	"os"
//	"path"
//	"path/filepath"
//	"runtime"
//	"strconv"
//	"strings"
//	"time"
//
//	"code.google.com/p/go-uuid/uuid"
//
//	"golang.org/x/net/context"
//
//	"github.com/golang/glog"
//	"github.com/vmware/govmomi"
//	"github.com/vmware/govmomi/find"
//	"github.com/vmware/govmomi/govc/host/esxcli"
//	"github.com/vmware/govmomi/vim25/methods"
//	"github.com/vmware/govmomi/vim25/mo"
//	"github.com/vmware/govmomi/vim25/soap"
//	"github.com/vmware/govmomi/vim25/types"
//)
//
//const (
//	propDescriptionLink = "dcp.descriptionLink"
//	propParentLink      = "dcp.parentLink"
//)
//
//type Client struct {
//	*govmomi.Client
//
//	dc     *govmomi.Datacenter
//	pool   *govmomi.ResourcePool
//	finder *find.Finder
//	state  *provisioning.ComputeStateWithDescription
//}
//
//// return error with hints for possible invalid adapterManagementReference
//func errorWithHints(err error, u url.URL) error {
//	var hints []string
//
//	if u.Scheme != "https" {
//		hints = append(hints, fmt.Sprintf("scheme `%s' is not 'https'", u.Scheme))
//	}
//
//	if u.Path != "/sdk" {
//		hints = append(hints, fmt.Sprintf("path `%s' is not '/sdk'", u.Path))
//	}
//
//	if u.User == nil {
//		hints = append(hints, "credentials not provided")
//	} else {
//		username := u.User.Username()
//		if username == "" {
//			hints = append(hints, "username not set")
//		}
//
//		password, _ := u.User.Password()
//		if password == "" {
//			hints = append(hints, "password not set")
//		}
//
//		// mask password in error message
//		u.User = url.UserPassword(username, strings.Repeat("x", len(password)))
//	}
//
//	if hints == nil {
//		return err
//	}
//
//	return fmt.Errorf("%s [adapterManagementReference(%s): %s]", err, u.String(), strings.Join(hints, ", "))
//}
//
//func NewClient(state *provisioning.ComputeStateWithDescription) (*Client, error) {
//	u, err := url.Parse(state.AdapterManagementReference)
//	if err != nil {
//		return nil, fmt.Errorf("failed to parse adapterManagementReference: %s", err)
//	}
//
//	c, err := govmomi.NewClient(*u, true)
//	if err != nil {
//		return nil, errorWithHints(err, *u)
//	}
//
//	client := &Client{
//		Client: c,
//		finder: find.NewFinder(c, false),
//		state:  state,
//	}
//
//	err = client.findDatacenter()
//	if err != nil {
//		return nil, errorWithHints(err, *u)
//	}
//
//	return client, nil
//}
//
//func isErrorNotFound(err error) bool {
//	// govmomi.Client error
//	if soap.IsSoapFault(err) {
//		switch soap.ToSoapFault(err).VimFault().(type) {
//		case types.ManagedObjectNotFound:
//			return true
//		}
//	}
//
//	// govmomi.Task error
//	if f, ok := err.(govmomi.HasFault); ok {
//		switch f.Fault().(type) {
//		case *types.FileNotFound:
//			return true
//		case *types.ManagedObjectNotFound:
//			return true
//		}
//	}
//
//	// govmomi.Finder error
//	switch err.(type) {
//	case *find.NotFoundError:
//		return true
//	}
//
//	// log error value type and caller to catch other cases where we might want return true
//	if _, file, line, ok := runtime.Caller(1); ok {
//		file = filepath.Base(file)
//		glog.Errorf("[esx/%s:%d] error type: %#v", file, line, err)
//	}
//
//	return false
//}
//
//func isErrorExists(err error) bool {
//	// govmomi.Client error
//	if soap.IsSoapFault(err) {
//		switch soap.ToSoapFault(err).VimFault().(type) {
//		case types.DuplicateName:
//			return true
//		}
//	}
//
//	// govmomi.Task error
//	if f, ok := err.(govmomi.HasFault); ok {
//		switch f.Fault().(type) {
//		case *types.FileAlreadyExists:
//			return true
//		}
//	}
//
//	return false
//}
//
//func (c *Client) Cleanup() {
//	if err := c.Logout(); err != nil {
//		glog.Errorf("esx client logout failed: %s", err)
//	}
//
//	c.CloseIdleConnections()
//}
//
//func (c *Client) CloseIdleConnections() {
//	c.Client.Client.Transport.(*http.Transport).CloseIdleConnections()
//}
//
//// TODO: this wrapper should live in govmomi
//func (c *Client) Logout() error {
//	req := types.Logout{
//		This: *c.ServiceContent.SessionManager,
//	}
//
//	_, err := methods.Logout(c.Client, &req)
//	return err
//}
//
//func (c *Client) findDatacenter() error {
//	var err error
//
//	if c.state.Description.DataCenterID == "" {
//		c.dc, err = c.finder.DefaultDatacenter()
//	} else {
//		c.dc, err = c.finder.Datacenter(c.state.Description.DataCenterID)
//	}
//
//	if err != nil {
//		return err
//	}
//
//	c.finder.SetDatacenter(c.dc)
//
//	return nil
//}
//
//func (c *Client) GetHostHealth(health *provisioning.ComputeHealthResponse) error {
//	host, err := c.finder.DefaultHostSystem()
//	if err != nil {
//		return err
//	}
//	var hs mo.HostSystem
//
//	props := []string{"summary.hardware", "summary.quickStats"}
//	err = c.Properties(host.Reference(), props, &hs)
//	if err != nil {
//		return err
//	}
//
//	hw := hs.Summary.Hardware
//	st := hs.Summary.QuickStats
//
//	health.TotalMemoryBytes = hw.MemorySize
//	health.UsedMemoryBytes = int64(st.OverallMemoryUsage) * 1024 * 1024
//
//	health.CPUCount = int64(hw.NumCpuPkgs * hw.NumCpuCores)
//	health.CPUTotalMhz = health.CPUCount * int64(hw.CpuMhz)
//	health.CPUUtilizationMhz = int64(st.OverallCpuUsage)
//
//	health.MemoryUtilizationPct = 100 * float64(health.UsedMemoryBytes) / float64(health.TotalMemoryBytes)
//	health.CPUUtilizationPct = 100 * float64(health.CPUUtilizationMhz) / float64(health.CPUTotalMhz)
//
//	return nil
//}
//
//func (c *Client) GetVirtualMachineHealth(health *provisioning.ComputeHealthResponse) error {
//	vm, err := c.VirtualMachine()
//	if err != nil {
//		return err
//	}
//
//	var hs mo.HostSystem
//	var o mo.VirtualMachine
//
//	props := []string{
//		"config.hardware",
//		"summary.quickStats",
//		"summary.runtime.host",
//	}
//
//	err = c.Properties(vm.Reference(), props, &o)
//	if err != nil {
//		return err
//	}
//
//	if o.Config == nil {
//		// from the sdk doc on the config property:
//		// "unavailable during the initial phases of virtual machine creation"
//		glog.Infof("vm %s config not yet available", c.state.ID)
//		return nil
//	}
//
//	hostRef := o.Summary.Runtime.Host
//	if hostRef == nil {
//		glog.Infof("vm %s not yet assigned to a host", c.state.ID)
//		return nil
//	}
//
//	err = c.Properties(*hostRef, []string{"summary.hardware"}, &hs)
//	if err != nil {
//		return err
//	}
//
//	hw := o.Config.Hardware
//	st := o.Summary.QuickStats
//
//	health.TotalMemoryBytes = int64(hw.MemoryMB) * 1024 * 1024
//	health.UsedMemoryBytes = int64(st.GuestMemoryUsage) * 1024 * 1024
//
//	health.CPUCount = int64(hw.NumCPU * hw.NumCoresPerSocket)
//	health.CPUTotalMhz = health.CPUCount * int64(hs.Summary.Hardware.CpuMhz)
//	health.CPUUtilizationMhz = int64(st.OverallCpuUsage)
//	if health.CPUUtilizationMhz == 0 {
//		health.CPUUtilizationMhz = 1 // keeping health stats check happy for the moment
//	}
//	health.MemoryUtilizationPct = 100 * float64(health.UsedMemoryBytes) / float64(health.TotalMemoryBytes)
//	health.CPUUtilizationPct = 100 * float64(health.CPUUtilizationMhz) / float64(health.CPUTotalMhz)
//
//	return nil
//}
//
//func (c *Client) VirtualMachine() (*govmomi.VirtualMachine, error) {
//	instanceUUID := true
//	vm, err := c.SearchIndex().FindByUuid(nil, c.state.ID, true, &instanceUUID)
//	if err != nil {
//		return nil, err
//	}
//
//	if vm == nil {
//		err = fmt.Errorf("vm with uuid=%s not found", c.state.ID)
//		return nil, err
//	}
//	return govmomi.NewVirtualMachine(c.Client, vm.Reference()), nil
//}
//
//func PowerState(state types.VirtualMachinePowerState) string {
//	switch state {
//	case types.VirtualMachinePowerStatePoweredOn:
//		return provisioning.PowerStateOn
//	case types.VirtualMachinePowerStatePoweredOff:
//		return provisioning.PowerStateOff
//	default:
//		return provisioning.PowerStateUnknown
//	}
//}
//
//func (c *Client) PowerState(vm *govmomi.VirtualMachine) (string, error) {
//	var o mo.VirtualMachine
//
//	err := c.Properties(vm.Reference(), []string{govmomi.PropRuntimePowerState}, &o)
//	if err != nil {
//		return "", err
//	}
//
//	return PowerState(o.Summary.Runtime.PowerState), nil
//}
//
//func (c *Client) UUID(vm *govmomi.VirtualMachine) (string, error) {
//	var o mo.VirtualMachine
//
//	err := c.Properties(vm.Reference(), []string{"config.uuid"}, &o)
//	if err != nil {
//		return "", err
//	}
//
//	return o.Config.Uuid, nil
//}
//
//func (c *Client) instanceUUID(vm *govmomi.VirtualMachine) (string, error) {
//	var o mo.VirtualMachine
//
//	err := c.Properties(vm.Reference(), []string{"config.instanceUuid"}, &o)
//	if err != nil {
//		return "", err
//	}
//
//	return o.Config.InstanceUuid, nil
//}
//
//var vmwOUI = net.HardwareAddr([]byte{0x0, 0xc, 0x29})
//
//// From http://pubs.vmware.com/vsphere-60/index.jsp?topic=%2Fcom.vmware.vsphere.networking.doc%2FGUID-DC7478FF-DC44-4625-9AD7-38208C56A552.html
//// "The host generates MAC addresses that consists of the VMware OUI 00:0C:29 and the last three octets in hexadecimal
////  format of the virtual machine UUID.  The virtual machine UUID is based on a hash calculated by using the UUID of the
////  ESXi physical machine and the path to the configuration file (.vmx) of the virtual machine."
//func macFromUUID(s string) (string, error) {
//	id := uuid.Parse(s)
//	if id == nil {
//		return "", fmt.Errorf("invalid uuid: %s", s)
//	}
//
//	mac := append(vmwOUI, id[len(id)-3:]...)
//	return mac.String(), nil
//}
//
//func (c *Client) PrimaryMac(vm *govmomi.VirtualMachine) (string, error) {
//	devices, err := vm.Device()
//	if err != nil {
//		return "", err
//	}
//
//	mac := devices.PrimaryMacAddress()
//
//	if mac == "" {
//		// VM has been created, but not powered on.
//		id, err := c.UUID(vm)
//		if err != nil {
//			return "", err
//		}
//
//		mac, err = macFromUUID(id)
//		if err != nil {
//			return "", err
//		}
//	}
//
//	return mac, nil
//}
//
//func (c *Client) Datastore() (*govmomi.Datastore, error) {
//	if c.state.Description.DataStoreID == "" {
//		return c.finder.DefaultDatastore()
//	}
//
//	return c.finder.Datastore(c.state.Description.DataStoreID)
//}
//
//func (c *Client) VMFolder() (*govmomi.Folder, error) {
//	folders, err := c.dc.Folders()
//	if err != nil {
//		return nil, err
//	}
//
//	return folders.VmFolder, nil
//}
//
//func (c *Client) CreateResourcePool(parentPath, name string) (*govmomi.ResourcePool, error) {
//	ipath := path.Join(parentPath, name)
//	glog.Infof("creating pool %s", ipath)
//
//	parent, err := c.finder.ResourcePool(parentPath)
//	if err != nil {
//		return nil, err
//	}
//
//	spec := types.ResourceConfigSpec{}
//	for _, ra := range []*types.ResourceAllocationInfo{&spec.CpuAllocation, &spec.MemoryAllocation} {
//		ra.ExpandableReservation = true
//		// VC requires the following fields to be set,
//		// even though doc/wsdl lists them as optional.
//		ra.Limit = -1      // unlimited
//		ra.Reservation = 1 // must be >= 0. field is tagged with "omitempty", so we set it to 1.
//		ra.ExpandableReservation = true
//		ra.Shares = new(types.SharesInfo)
//		ra.Shares.Level = types.SharesLevelNormal
//	}
//
//	pool, err := parent.Create(name, spec)
//
//	if err != nil && isErrorExists(err) {
//		// Someone else won the race to create this pool
//		glog.Warningf("pool %s already exists", ipath)
//		return c.finder.ResourcePool(ipath)
//	}
//
//	return pool, err
//}
//
//func (c *Client) ParentResourcePool() (*govmomi.ResourcePool, error) {
//	if c.state.Description.ZoneID == "" {
//		return c.finder.DefaultResourcePool()
//	}
//
//	return c.finder.ResourcePool(c.state.Description.ZoneID)
//}
//
//func (c *Client) getResourcePool(ctx context.Context, create bool) (*govmomi.ResourcePool, error) {
//	if c.pool != nil {
//		return c.pool, nil
//	}
//
//	state := &provisioning.ResourcePoolState{}
//	u := uri.Extend(uri.Local(), c.state.ResourcePoolLink)
//	err := provisioning.GetResourcePoolState(ctx, state, u)
//	if err != nil {
//		return nil, err
//	}
//
//	parentPool, err := c.ParentResourcePool()
//	if err != nil {
//		return nil, err
//	}
//	parentPath := parentPool.InventoryPath
//
//	name := state.Name
//	if name == "" {
//		name = state.ID
//	}
//
//	c.pool, err = c.finder.ResourcePool(path.Join(parentPath, name))
//
//	// Create the pool if it doesn't already exist
//	if err != nil && isErrorNotFound(err) {
//		if create {
//			c.pool, err = c.CreateResourcePool(parentPath, name)
//		}
//	}
//
//	return c.pool, err
//}
//
//// Retrieve the set of given properties from all VirtualMachines in the ResourcePool
//func (c *Client) getVirtualMachineProperties(ctx context.Context, props []string) ([]mo.VirtualMachine, error) {
//	host, err := c.finder.DefaultHostSystem()
//	if err != nil {
//		if isErrorNotFound(err) {
//			return nil, nil
//		}
//		return nil, err
//	}
//
//	ospec := types.ObjectSpec{
//		Obj: host.Reference(),
//		SelectSet: []types.BaseSelectionSpec{
//			&types.TraversalSpec{
//				Type: "HostSystem",
//				Path: "vm",
//				Skip: false,
//			},
//		},
//		Skip: false,
//	}
//
//	pspec := types.PropertySpec{
//		Type:    "VirtualMachine",
//		PathSet: props,
//	}
//
//	req := types.RetrieveProperties{
//		This: c.ServiceContent.PropertyCollector,
//		SpecSet: []types.PropertyFilterSpec{
//			{
//				ObjectSet: []types.ObjectSpec{ospec},
//				PropSet:   []types.PropertySpec{pspec},
//			},
//		},
//	}
//
//	var vms []mo.VirtualMachine
//	err = mo.RetrievePropertiesForRequest(c.Client, req, &vms)
//	if err != nil {
//		return nil, err
//	}
//
//	return vms, nil
//}
//
//func (c *Client) Network() (govmomi.NetworkReference, error) {
//	if c.state.Description.NetworkID == "" {
//		return c.finder.DefaultNetwork()
//	}
//
//	return c.finder.Network(c.state.Description.NetworkID)
//}
//
//var bootDeviceTypes = map[string]string{
//	provisioning.BootDeviceCdrom:   govmomi.DeviceTypeCdrom,
//	provisioning.BootDeviceFloppy:  govmomi.DeviceTypeFloppy,
//	provisioning.BootDeviceDisk:    govmomi.DeviceTypeDisk,
//	provisioning.BootDeviceNetwork: govmomi.DeviceTypeEthernet,
//}
//
//func (c *Client) SetBootOrder(vm *govmomi.VirtualMachine, bootDeviceOrder []string) error {
//	var order []string
//
//	for _, bootType := range bootDeviceOrder {
//		vtype, ok := bootDeviceTypes[bootType]
//		if !ok {
//			return fmt.Errorf("unknown boot device type: %s", bootType)
//		}
//
//		order = append(order, vtype)
//	}
//
//	options, err := vm.BootOptions()
//	if err != nil {
//		return err
//	}
//
//	devices, err := vm.Device()
//	if err != nil {
//		return err
//	}
//
//	options.BootOrder = devices.BootOrder(order)
//
//	return vm.SetBootOptions(options)
//}
//
//func (c *Client) NetworkDevice() (types.BaseVirtualDevice, error) {
//	net, err := c.Network()
//	if err != nil {
//		return nil, err
//	}
//
//	backing, err := net.EthernetCardBackingInfo()
//	if err != nil {
//		return nil, err
//	}
//
//	return govmomi.EthernetCardTypes().CreateEthernetCard("", backing)
//}
//
//func (c *Client) sourceImagePath(ctx context.Context, diskState *provisioning.DiskState, dirPath string) (string, error) {
//	ds, err := c.Datastore()
//	if err != nil {
//		return "", err
//	}
//
//	var filePath string
//
//	// Don't cache boot config disks locally
//	if diskState.BootConfig != nil {
//		defer func() {
//			// Remove filePath if it was set
//			if filePath != "" {
//				_ = os.Remove(filePath)
//			}
//		}()
//	}
//
//	if diskState.SourceImageReference != "" {
//		u, err := url.Parse(diskState.SourceImageReference)
//		if err != nil {
//			return "", err
//		}
//
//		switch u.Scheme {
//		case "file":
//			return ds.Path(u.Path), nil
//		case "http", "https":
//			filePath, err = image.Download(ctx, &c.state.ComputeState, diskState)
//			if err != nil {
//				err = fmt.Errorf("error downloading image for %s: %s", c.state.ComputeState.SelfLink, err)
//				return "", err
//			}
//		default:
//			return "", fmt.Errorf("scheme '%s' not supported", u.Scheme)
//		}
//	}
//
//	// Fall back on image download helper to find the image
//	if filePath == "" {
//		filePath, err = image.Download(ctx, &c.state.ComputeState, diskState)
//		if err != nil {
//			return "", err
//		}
//	}
//
//	// Upload file to the VM's folder, file is deleted w/ InstanceRequestTypeDelete.
//	// We could cache non boot-config disks on the datastore, but:
//	// - Can't update a file that is attached to another VM.
//	// - Don't have a good houskeeping method to delete such unused files.
//	// - Production usage should be rare, only as a bootstrapping method.
//
//	name := path.Join(dirPath, path.Base(filePath))
//	u, err := ds.URL(c.dc, name)
//	if err != nil {
//		return "", err
//	}
//
//	glog.Infof("Uploading %s -> %s", filePath, u)
//
//	err = c.Client.Client.UploadFile(filePath, u, nil)
//	if err != nil {
//		return "", err
//	}
//
//	return ds.Path(name), nil
//}
//
//func (c *Client) AddDisks(ctx context.Context, vm *govmomi.VirtualMachine, diskLinks []string) ([]*provisioning.DiskState, error) {
//	dir, err := c.vmDirectory(vm)
//	if err != nil {
//		return nil, err
//	}
//
//	dirPath, err := c.vmDirectoryPath(dir)
//	if err != nil {
//		return nil, err
//	}
//
//	devices, err := vm.Device()
//	if err != nil {
//		return nil, err
//	}
//
//	diskController, err := devices.FindSCSIController("")
//	if err != nil {
//		return nil, err
//	}
//
//	ideController, err := devices.FindIDEController("")
//	if err != nil {
//		return nil, err
//	}
//
//	var disks []types.BaseVirtualDevice
//
//	diskStates, err := provisioning.GetDiskState(ctx, diskLinks)
//	if err != nil {
//		return nil, err
//	}
//
//	for _, diskState := range diskStates {
//		switch diskState.DiskType {
//		case provisioning.DiskTypeHDD:
//			if diskState.SourceImageReference != "" {
//				diskName, err := c.sourceImagePath(ctx, diskState, dirPath)
//				if err != nil {
//					return nil, err
//				}
//
//				disk := devices.CreateDisk(diskController, diskName)
//				disk = devices.ChildDisk(disk) // TODO: support full clone
//				disks = append(disks, disk)
//			} else {
//				diskName := path.Join(dir, diskState.ID)
//
//				disk := devices.CreateDisk(diskController, diskName)
//				disk.CapacityInKB = diskState.CapacityMBytes * 1024
//				disks = append(disks, disk)
//			}
//		case provisioning.DiskTypeCdrom:
//			cdrom, err := devices.CreateCdrom(ideController)
//			if err != nil {
//				return nil, err
//			}
//
//			isoPath, err := c.sourceImagePath(ctx, diskState, dirPath)
//			if err != nil {
//				return nil, err
//			}
//
//			disks = append(disks, devices.InsertIso(cdrom, isoPath))
//		case provisioning.DiskTypeFloppy:
//			floppy, err := devices.CreateFloppy()
//			if err != nil {
//				return nil, err
//			}
//
//			imgPath, err := c.sourceImagePath(ctx, diskState, dirPath)
//			if err != nil {
//				return nil, err
//			}
//
//			disks = append(disks, devices.InsertImg(floppy, imgPath))
//		case provisioning.DiskTypeNetwork:
//			continue
//		default:
//			return nil, provisioning.ErrUnsupportedDiskType
//		}
//
//		diskState.DiskStatus = provisioning.DiskStatusAttached
//
//		diskStates = append(diskStates, diskState)
//	}
//
//	err = vm.AddDevice(disks...)
//	if err != nil {
//		return nil, err
//	}
//
//	return diskStates, nil
//}
//
//func (c *Client) CreateVM(ctx context.Context) (*govmomi.VirtualMachine, error) {
//	folder, err := c.VMFolder()
//	if err != nil {
//		return nil, err
//	}
//
//	pool, err := c.getResourcePool(ctx, true)
//	if err != nil {
//		return nil, err
//	}
//
//	ds, err := c.Datastore()
//	if err != nil {
//		return nil, err
//	}
//
//	name := c.state.ID
//	customProp := c.state.CustomProperties
//	if customProp != nil {
//		if dispName, ok := customProp["displayName"]; ok {
//			name = dispName
//		}
//	}
//
//	// Use a full path to the config file to avoid creating a VM with the same name
//	cfgPath := fmt.Sprintf("[%s] %s/%s.vmx", ds.Name(), name, name)
//
//	spec := types.VirtualMachineConfigSpec{
//		Name:  name,
//		Files: &types.VirtualMachineFileInfo{VmPathName: cfgPath},
//
//		// set link properties for use by enumeration service
//		ExtraConfig: []types.BaseOptionValue{
//			&types.OptionValue{
//				Key:   propDescriptionLink,
//				Value: c.state.DescriptionLink,
//			},
//			&types.OptionValue{
//				Key:   propParentLink,
//				Value: c.state.ParentLink,
//			},
//		},
//
//		NumCPUs:  int(c.state.Description.CPUCount),
//		MemoryMB: c.state.Description.TotalMemoryBytes / (1024 * 1024),
//		GuestId:  string(types.VirtualMachineGuestOsIdentifierOtherGuest64),
//	}
//
//	var devices govmomi.VirtualDeviceList
//
//	// add network card
//	nic, err := c.NetworkDevice()
//	if err != nil {
//		return nil, err
//	}
//	devices = append(devices, nic)
//
//	diskController, err := devices.CreateSCSIController("")
//	if err != nil {
//		return nil, err
//	}
//	devices = append(devices, diskController)
//
//	for _, device := range devices {
//		config := &types.VirtualDeviceConfigSpec{
//			Operation: types.VirtualDeviceConfigSpecOperationAdd,
//			Device:    device,
//		}
//
//		spec.DeviceChange = append(spec.DeviceChange, config)
//	}
//
//	glog.Infof("creating vm %s", name)
//
//	task, err := folder.CreateVM(spec, pool, nil)
//	if err != nil {
//		return nil, err
//	}
//
//	info, err := task.WaitForResult(nil)
//	if err != nil {
//		return nil, err
//	}
//
//	vm := govmomi.NewVirtualMachine(c.Client, info.Result.(types.ManagedObjectReference))
//
//	return vm, nil
//}
//
//func (c *Client) CreateInstance(ctx context.Context, u uri.URI) error {
//	vm, err := c.CreateVM(ctx)
//	if err != nil {
//		if isErrorExists(err) {
//			// Someone else won the race to create this vm
//			glog.Warningf("vm %s already exists", c.state.ID)
//			return nil
//		}
//		return err
//	}
//
//	state := &provisioning.ComputeState{
//		ResourcePoolLink: c.state.ResourcePoolLink,
//		PowerState:       provisioning.PowerStateOff,
//	}
//
//	state.ID, err = c.instanceUUID(vm)
//	if err != nil {
//		return err
//	}
//
//	state.PrimaryMAC, err = c.PrimaryMac(vm)
//	if err != nil {
//		return err
//	}
//
//	state.CustomProperties = map[string]string{}
//	state.CustomProperties[FieldNameManagedObjectReference] = GetMorefAsString(&vm.ManagedObjectReference)
//
//	diskStates, err := c.AddDisks(ctx, vm, c.state.DiskLinks)
//	if err != nil {
//		return err
//	}
//
//	err = provisioning.PatchDiskState(ctx, diskStates)
//	if err != nil {
//		return err
//	}
//
//	return provisioning.PatchComputeState(ctx, u, state)
//}
//
//func (c *Client) DeleteVM() error {
//	vm, err := c.VirtualMachine()
//	if err != nil {
//		return err
//	}
//
//	task, err := vm.PowerOff()
//	if err != nil {
//		return err
//	}
//
//	// Ignore error since the VM may already been in powered off state.
//	// vm.Destroy will fail if the VM is still powered on.
//	_ = task.Wait()
//
//	task, err = vm.Destroy()
//	if err != nil {
//		return err
//	}
//
//	return task.Wait()
//}
//
//func (c *Client) DeleteVMFolder(vmpathname string) error {
//	ds, err := c.Datastore()
//	if err != nil {
//		return err
//	}
//	folder := ds.Path(vmpathname)
//	task, err := c.FileManager().DeleteDatastoreFile(folder, c.dc)
//	if err != nil {
//		return err
//	}
//
//	return task.Wait()
//}
//
//func (c *Client) vmfolderName(vm *govmomi.VirtualMachine) (string, error) {
//	dir, err := c.vmDirectory(vm)
//	if err != nil {
//		return "", err
//	}
//
//	folderName, err := c.vmDirectoryPath(dir)
//	if err != nil {
//		return "", err
//	}
//
//	return folderName, nil
//}
//
//func (c *Client) vmDirectoryPath(dir string) (string, error) {
//	//Extract directory path
//	startIndex := strings.Index(dir, "]")
//	if startIndex == -1 {
//		return "", fmt.Errorf("Failed to get VM directory path from directory %s", dir)
//	}
//
//	folderPath := strings.TrimSpace(dir[startIndex+1 : len(dir)])
//	return folderPath, nil
//}
//
//func (c *Client) vmDirectory(vm *govmomi.VirtualMachine) (string, error) {
//	var mvm mo.VirtualMachine
//	err := c.Properties(vm.Reference(), []string{"summary.config.vmPathName"}, &mvm)
//	if err != nil {
//		return "", err
//	}
//
//	dir := path.Dir(mvm.Summary.Config.VmPathName)
//	return dir, nil
//}
//
//func (c *Client) CreateSnapshot(name string, description string, CustomProperties map[string]string) error {
//	vm, err := c.VirtualMachine()
//	if err != nil {
//		return err
//	}
//
//	memoryFlag, err := strconv.ParseBool(CustomProperties["memoryFlag"])
//	if err != nil {
//		return err
//	}
//	quiesceFlag, err := strconv.ParseBool(CustomProperties["quiesceFlag"])
//	if err != nil {
//		return err
//	}
//
//	task, err := vm.Snapshot(name, description, memoryFlag, quiesceFlag)
//	if err != nil {
//		return err
//	}
//	return task.Wait()
//}
//
//func (c *Client) DeleteResourcePool(ctx context.Context) error {
//	pool, err := c.getResourcePool(ctx, false)
//	if err != nil {
//		return err
//	}
//
//	var p mo.ResourcePool
//	err = c.Properties(pool.Reference(), []string{"name", "vm"}, &p)
//	if err != nil {
//		return err
//	}
//
//	glog.Infof("%d VMs in pool %s", len(p.Vm), p.Name)
//
//	if len(p.Vm) > 0 {
//		return nil
//	}
//
//	task, err := pool.Destroy()
//
//	if err != nil {
//		return err
//	}
//
//	return task.Wait()
//}
//
//func (c *Client) DeleteInstance(ctx context.Context) error {
//	vm, err := c.VirtualMachine()
//	if err != nil {
//		return err
//	}
//	vmPathName, err := c.vmfolderName(vm)
//	if err != nil {
//		glog.Errorf("Failed to get VM folder name %s", vmPathName)
//	}
//
//	if err := c.DeleteVM(); err != nil {
//		if isErrorNotFound(err) {
//			// Someone else won the race to delete this vm
//			glog.Warningf("vm %s already deleted", c.state.ID)
//		} else {
//			return err
//		}
//	}
//	// vm.Destroy() does not delete files we may have uploaded,
//	// such as a boot config image.
//	if err := c.DeleteVMFolder(vmPathName); err != nil {
//		if !isErrorNotFound(err) {
//			return err
//		}
//	}
//	if err := c.DeleteResourcePool(ctx); err != nil {
//		if isErrorNotFound(err) {
//			// Someone else won the race to delete this pool
//			glog.Warningf("pool %s already deleted", path.Base(c.state.ResourcePoolLink))
//		} else {
//			return err
//		}
//	}
//	return provisioning.DeleteComputeState(ctx, &c.state.ComputeState)
//}
//
//// findGuestIP returns an IPv4 address for the given mac address
//// or empty string if not found.
//func (c *Client) findGuestIP(vm *govmomi.VirtualMachine, mac string) (string, error) {
//	var mvm mo.VirtualMachine
//	ref := vm.Reference()
//	props := []string{"guest.net"}
//
//	err := c.Properties(ref, props, &mvm)
//	if err != nil {
//		return "", err
//	}
//
//	if mvm.Guest == nil {
//		return "", os.ErrNotExist // vmware-tools not running (yet)
//	}
//
//	for _, nic := range mvm.Guest.Net {
//		if nic.MacAddress != mac {
//			continue
//		}
//
//		for _, ip := range nic.IpConfig.IpAddress {
//			if net.ParseIP(ip.IpAddress).To4() != nil {
//				return ip.IpAddress, nil
//			}
//		}
//	}
//
//	return "", os.ErrNotExist
//}
//
//// WaitForIP polls ESX for the VM's IP
//func (c *Client) WaitForIP(ctx context.Context, vm *govmomi.VirtualMachine) (string, error) {
//	ctx, cancel := context.WithTimeout(ctx, time.Minute*5)
//	defer cancel()
//
//	guest := esxcli.NewGuestInfo(c.Client)
//	mac, err := c.PrimaryMac(vm)
//	if err != nil {
//		return "", err
//	}
//
//	ticker := time.NewTicker(time.Second * 10)
//	defer ticker.Stop()
//
//	for {
//		select {
//		case <-ctx.Done():
//			return "", fmt.Errorf("wait for IP(%s): %s", vm.Reference(), ctx.Err())
//		case <-ticker.C:
//			ip, err := guest.IpAddress(vm)
//
//			if err == nil && ip != "0.0.0.0" {
//				return ip, nil
//			}
//
//			// TODO: Once Context related changes are complete with govmomi,
//			// we can cancel a WaitForProperties call and wait for changes instead.
//			// Until then, poll as we do with the esxcli path.
//			ip, err = c.findGuestIP(vm, mac)
//			if err != nil {
//				continue
//			}
//			return ip, nil
//		}
//	}
//}
