package provisioning

import "dcp/uri"

type DiskCustomizationRequest struct {
	ComputeReference uri.URI `json:"computeReference"`
	DiskReference    uri.URI `json:"diskReference"`
}
