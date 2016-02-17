package provisioning

import (
	"dcp/common"
	"dcp/uri"
)

const (
	FirewallInstanceRequestTypeCreate = "CREATE"
	FirewallInstanceRequestTypeDelete = "DELETE"
)

type FirewallInstanceRequest struct {
	common.ServiceDocument

	RequestType               string  `json:"requestType"`
	AuthCredentialsLink       string  `json:"authCredentialsLink,omitempty"`
	ResourcePoolLink          string  `json:"resourcePoolLink,omitempty"`
	FirewallReference         uri.URI `json:"firewallReference"`
	ProvisioningTaskReference uri.URI `json:"provisioningTaskReference"`
	IsMockRequest             bool    `json:"isMockRequest"`
}
