package provisioning

import (
	"dcp/common"
)

type SnapshotState struct {
	common.ServiceDocument

	ID               string            `json:"id,omitempty"`
	DescriptionLink  string            `json:"descriptionLink,omitempty"`
	ComputeLink      string            `json:"computeLink"`
	Name             string            `json:"name,omitempty"`
	Description      string            `json:"description,omitempty"`
	CustomProperties map[string]string `json:"customProperties,omitempty"`
}
