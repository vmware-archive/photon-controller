package provisioning

import "dcp/common"

const (
	FieldNameDescriptionLink            = "descriptionLink"
	FieldNameResourcePoolLink           = "resourcePoolLink"
	FieldNameAdapterManagementReference = "adapterManagementReference"
	FieldNameParentLink                 = "parentLink"
	FieldNameID                         = "id"
	FieldNameDisplayName                = "displayName"
	FieldNameComputeType                = "computeType"
	FieldNameCustomProperties           = "customProperties"
	ComputeStateDocumentKind            = "com:vmware:photon:provisioning:services:ComputeService:ComputeState"
)
const (
	PowerStateOn      = "ON"
	PowerStateOff     = "OFF"
	PowerStateUnknown = "UNKNOWN"
	PowerStateSuspend = "SUSPEND"
)

const (
	PowerTransitionSoft = "SOFT"
	PowerTransitionHard = "HARD"
)

const (
	BootDeviceCdrom   = "CDROM"
	BootDeviceFloppy  = "FLOPPY"
	BootDeviceDisk    = "DISK"
	BootDeviceNetwork = "NETWORK"
)

const (
	CustomPropertyNameRuntimeInfo = "runtimeInfo"
)

const (
	ComputeTypeVMHost          = "VM_HOST"
	ComputeTypeVMGuest         = "VM_GUEST"
	ComputeTypeDockerContainer = "DOCKER_CONTAINER"
	ComputeTypePhysical        = "PHYSICAL"
	ComputeTypeOsOnPhysical    = "OS_ON_PHYSICAL"
)

type ComputeState struct {
	common.ServiceDocument

	ID                         string            `json:"id,omitempty"`
	DescriptionLink            string            `json:"descriptionLink,omitempty"`
	ResourcePoolLink           string            `json:"resourcePoolLink,omitempty"`
	Address                    string            `json:"address,omitempty"`
	PrimaryMAC                 string            `json:"primaryMAC,omitempty"`
	PowerState                 string            `json:"powerState"`
	ParentLink                 string            `json:"parentLink,omitempty"`
	AdapterManagementReference string            `json:"adapterManagementReference,omitempty"`
	DiskLinks                  []string          `json:"diskLinks,omitempty"`
	NetworkLinks               []string          `json:"networkLinks,omitempty"`
	CustomProperties           map[string]string `json:"customProperties,omitempty"`
	TenantLinks                []string          `json:"tenantLinks,omitempty"`
}

type ComputeDescription struct {
	common.ServiceDocument

	ID                          string   `json:"id,omitempty"`
	Name                        string   `json:"name,omitempty"`
	SupportedChildren           []string `json:"supportedChildren"`
	CPUCount                    int64    `json:"cpuCount"`
	CPUMhzPerCore               int64    `json:"cpuMhzPerCore"`
	TotalMemoryBytes            int64    `json:"totalMemoryBytes"`
	PowerAdapterReference       string   `json:"powerAdapterReference,omitempty"`
	BootAdapterReference        string   `json:"bootAdapterReference,omitempty"`
	HealthAdapterReference      string   `json:"healthAdapterReference,omitempty"`
	EnumerationAdapterReference string   `json:"enumerationAdapterReference,omitempty"`
	InstanceAdapterReference    string   `json:"instanceAdapterReference,omitempty"`
	AuthCredentialsLink         string   `json:"authCredentialsLink,omitempty"`
	EnvironmentName             string   `json:"environmentName,omitempty"`
	DataCenterID                string   `json:"dataCenterId,omitempty"`
	DataStoreID                 string   `json:"dataStoreId,omitempty"`
	NetworkID                   string   `json:"networkId,omitempty"`
	RegionID                    string   `json:"regionId,omitempty"`
	ZoneID                      string   `json:"zoneId,omitempty"`
	TenantLinks                 []string `json:"tenantLinks,omitempty"`
}

type ComputeStateWithDescription struct {
	ComputeState

	Description ComputeDescription `json:"description,omitempty"`
}
