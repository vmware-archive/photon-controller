package types

import (
	dcp "dcp/common"
)

type AcquireLeaseRequest struct {
	Kind string `json:"kind"`
	MAC  MAC    `json:"mac"`
}

func NewAcquireLeaseRequest(mac MAC) *AcquireLeaseRequest {
	req := &AcquireLeaseRequest{
		Kind: "com:vmware:photon:controller:provisioner:xenon:entity:DhcpSubnetService:AcquireLeaseRequest",
		MAC:  mac,
	}

	return req
}

type ReleaseLeaseRequest struct {
	Kind string `json:"kind"`
	MAC  MAC    `json:"mac"`
}

func NewReleaseLeaseRequest(mac MAC) *ReleaseLeaseRequest {
	req := &ReleaseLeaseRequest{
		Kind: "com:vmware:photon:controller:provisioner:xenon:entity:DhcpSubnetService:ReleaseLeaseRequest",
		MAC:  mac,
	}

	return req
}

type Subnet struct {
	dcp.ServiceDocument

	ID     string  `json:"id"`
	Subnet IPNet   `json:"subnetAddress"`
	Ranges []Range `json:"ranges"`
}

type Range struct {
	Low  IP `json:"low"`
	High IP `json:"high"`
}

type Lease struct {
	dcp.ServiceDocument

	IP                     IP     `json:"ip,omitempty"`
	MAC                    MAC    `json:"mac"`
	NetworkDescriptionLink string `json:"networkDescriptionLink"`

	Description Subnet `json:"description"`
}

type Configuration struct {
	dcp.ServiceDocument

	// Whether DHCP should be enabled for this machine or not.
	// Use the subnet-wide parameter to enable/disable DHCP by default.
	Enabled bool `json:"isEnabled"`

	Image string `json:"hostBootImageReference,omitempty"`

	LeaseTimeSec uint64 `json:"leaseDurationTimeMicros,omitempty"`

	Routers     []IP              `json:"routerAddresses,omitempty"`
	NameServers []IP              `json:"nameServerAddresses,omitempty"`
	ComputeStateReference string `json:"computeStateReference,omitempty"`
	DiskStateReference string `json:"diskStateReference,omitempty"`
}
