package provisioning

import (
	"dcp/common"
)

type NetworkState struct {
	common.ServiceDocument

	ID               string            `json:"id,omitempty"`
	Name             string            `json:"name,omitempty"`
	SubnetCIDR       string            `json:"subnetCIDR,omitempty"`
	CustomProperties map[string]string `json:"customProperties,omitempty"`
	TenantLinks      []string          `json:"tenantLinks,omitempty"`
}
