package provisioning

import "dcp/uri"

type ComputePowerRequest struct {
	ComputeReference          uri.URI `json:"computeReference"`
	ProvisioningTaskReference uri.URI `json:"provisioningTaskReference"`
	PowerState                string  `json:"powerState"`
	PowerTransition           string  `json:"powerTransition"`
	IsMockRequest             bool    `json:"isMockRequest"`
}
