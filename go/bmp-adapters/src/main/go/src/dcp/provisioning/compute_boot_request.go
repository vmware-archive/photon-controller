package provisioning

import "dcp/uri"

type ComputeBootRequest struct {
	ComputeReference          uri.URI  `json:"computeReference"`
	ProvisioningTaskReference uri.URI  `json:"provisioningTaskReference"`
	BootDeviceOrder           []string `json:"bootDeviceOrder"`
	IsMockRequest             bool     `json:"isMockRequest"`
}
