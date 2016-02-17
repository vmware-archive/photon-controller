package provisioning

const (
	Provisioning = "/provisioning"
	DhcpService  = Provisioning + "/dhcp"

	Resources           = "/resources"
	Compute             = Resources + "/compute"
	ComputeDescriptions = Resources + "/compute-descriptions"
	Disks               = Resources + "/disks"
	NetworkInterfaces   = Resources + "/networks/interfaces"

	ProvisioningEsx     = Provisioning + "/esx"
	BootServiceEsx      = ProvisioningEsx + "/boot-service"
	PowerServiceEsx     = ProvisioningEsx + "/power-service"
	SnapshotServiceEsx  = ProvisioningEsx + "/snapshot-service"
	InstanceServiceEsx  = ProvisioningEsx + "/instance-service"
	HealthServiceEsx    = ProvisioningEsx + "/health-service"
	EnumerateServiceEsx = ProvisioningEsx + "/enumeration-service"

	ProvisioningGce     = Provisioning + "/gce"
	InstanceServiceGce  = ProvisioningGce + "/instance-service"
	SecretsServiceGce   = ProvisioningGce + "/clientsecrets"
	HealthServiceGce    = ProvisioningGce + "/health-service"
	EnumerateServiceGce = ProvisioningGce + "/enumeration-service"
	NetworkServiceGce   = ProvisioningGce + "/network-service"
	FirewallServiceGce  = ProvisioningGce + "/firewall-service"

	ProvisioningEsxCloud     = Provisioning + "/esxcloud"
	BootServiceEsxCloud      = ProvisioningEsxCloud + "/boot-service"
	InstanceServiceEsxCloud  = ProvisioningEsxCloud + "/instance-service"
	SecretsServiceEsxCloud   = ProvisioningEsxCloud + "/clientsecrets"
	HealthServiceEsxCloud    = ProvisioningEsxCloud + "/health-service"
	EnumerateServiceEsxCloud = ProvisioningEsxCloud + "/enumeration-service"

	ProvisioningDell       = Provisioning + "/dell"
	ProvisioningDellDrac5  = ProvisioningDell + "/drac5"
	BootServiceDellDrac5   = ProvisioningDellDrac5 + "/boot-service"
	ProvisioningDellIdrac7 = ProvisioningDell + "/idrac7"
	BootServiceDellIdrac7  = ProvisioningDellIdrac7 + "/boot-service"

	ProvisioningIPMI = Provisioning + "/ipmi"
	BootServiceIPMI  = ProvisioningIPMI + "/boot-service"
	PowerServiceIPMI = ProvisioningIPMI + "/power-service"

	ProvisioningDocker    = Provisioning + "/docker"
	InstanceServiceDocker = ProvisioningDocker + "/instance-service"
	HealthServiceDocker   = ProvisioningDocker + "/health-service"

	HealthServiceKubernetes = HealthServiceDocker + "/kubernetes"

	ImageFormatIso       = "iso"
	ImageFormatFat       = "img"
	ImageFormatTar       = "tar"
	BootConfigService    = Provisioning + "/boot-config-service"
	BootConfigServiceIso = BootConfigService + "/" + ImageFormatIso
	BootConfigServiceFat = BootConfigService + "/" + ImageFormatFat
	BootConfigServiceTar = BootConfigService + "/" + ImageFormatTar

	SSHCommandService = Provisioning + "/ssh-service"

	ProvisioningNetwork      = Provisioning + "/network"
	OvsBridgeInstanceService = ProvisioningNetwork + "/ovs-instance-service"

	QueryExpand                 = "expand"
	QueryComputeDescriptionLink = "descriptionLink"
	QueryDocumentSelfLinkLink   = "documentSelfLink"
)
