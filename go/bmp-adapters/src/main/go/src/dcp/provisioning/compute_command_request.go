package provisioning

import "dcp/uri"

type ComputeCommandReference struct {
	ComputeReference          uri.URI  `json:"computeReference"`
	ProvisioningTaskReference uri.URI  `json:"provisioningTaskReference"`
	HostCommandReference      string   `json:"hostCommandReference,omitempty"`
	Commands                  []string `json:"commands"`
	IsMockRequest             bool     `json:"isMockRequest"`
}
