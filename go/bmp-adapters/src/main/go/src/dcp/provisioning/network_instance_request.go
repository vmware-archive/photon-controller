package provisioning

import (
	"dcp/common"
	"dcp/uri"
)

const (
	NetworkInstanceRequestTypeCreate = "CREATE"
	NetworkInstanceRequestTypeDelete = "DELETE"
)

type NetworkInstanceRequest struct {
	common.ServiceDocument

	RequestType               string  `json:"requestType"`
	AuthCredentialsLink       string  `json:"authCredentialsLink,omitempty"`
	ResourcePoolLink          string  `json:"resourcePoolLink,omitempty"`
	NetworkReference          uri.URI `json:"networkReference"`
	ProvisioningTaskReference uri.URI `json:"provisioningTaskReference"`
	IsMockRequest             bool    `json:"isMockRequest"`
}
