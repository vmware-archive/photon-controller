package provisioning

import "dcp/uri"

const (
	EnumerationActionStart   = "START"   // Start enumeration service in listening mode
	EnumerationActionStop    = "STOP"    // Stop enumeration service if it's running in listening mode
	EnumerationActionRefresh = "REFRESH" // Run enumeration service once
)

type ComputeEnumerateRequest struct {
	ResourcePoolLink           string  `json:"resourcePoolLink"`
	ComputeDescriptionLink     string  `json:"computeDescriptionLink"`
	AdapterManagementReference string  `json:"adapterManagementReference,omitempty"`
	EnumerationAction          string  `json:"enumerationAction"`
	ParentComputeLink          string  `json:"parentComputeLink"`
	EnumerationTaskReference   uri.URI `json:"enumerationTaskReference"`
}
