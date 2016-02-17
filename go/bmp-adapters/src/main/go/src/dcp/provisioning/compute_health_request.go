package provisioning

import "dcp/uri"

type ComputeHealthRequest struct {
	ComputeReference uri.URI `json:"computeReference"`
}
