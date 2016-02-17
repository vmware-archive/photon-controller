package provisioning

import (
	"dcp/common"
)

const (
	ProtocolTCP  = "tcp"
	ProtocolUDP  = "udp"
	ProtocolICMP = "icmp"
)

type FirewallState struct {
	common.ServiceDocument

	ID               string            `json:"id,omitempty"`
	NetworkLink      string            `json:"networkDescriptionLink,omitempty"`
	Ingress          []Allow           `json:"ingress,omitempty"`
	Egress           []Allow           `json:"egress,omitempty"`
	CustomProperties map[string]string `json:"customProperties,omitempty"`
	TenantLinks      []string          `json:"tenantLinks,omitempty"`
}

type Allow struct {
	Name     string   `json:"name,omitempty"`
	Protocol string   `json:"protocol,omitempty"`
	IPRange  string   `json:"IPRange,omitempty"`
	Ports    []string `json:"ports,omitempty"`
}
