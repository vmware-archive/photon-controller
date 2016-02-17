package provisioning

import (
	"dcp/uri"

	"errors"
)

const (
	InstanceRequestTypeCreate   = "CREATE"
	InstanceRequestTypeDelete   = "DELETE"
	InstanceRequestTypeValidate = "VALIDATE_CREDENTIALS"
)

var (
	ErrUnsupportedRequestType = errors.New("unsupported ComputeInstanceRequest.InstanceRequestType")
)

type ComputeInstanceRequest struct {
	ComputeReference          uri.URI `json:"computeReference,omitempty"`
	ProvisioningTaskReference uri.URI `json:"provisioningTaskReference,omitempty"`
	AuthCredentialsLink       string  `json:"authCredentialsLink,omitempty"`
	RequestType               string  `json:"requestType"`
	IsMockRequest             bool    `json:"isMockRequest"`
}
