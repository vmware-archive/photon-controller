package provisioning

import (
	"dcp/client"
	"dcp/common"
	"dcp/operation"
	"dcp/uri"

	"golang.org/x/net/context"
)

type NetworkBridgeState struct {
	common.ServiceDocument

	NetworkDescriptionLink string   `json:"networkDescriptionLink,omitempty"`
	ResourcePoolLink       string   `json:"resourcePoolLink,omitempty"`
	BridgePeerAddresses    []string `json:"bridgePeerAddresses"`
}

// GetNetworkBridgeState retrieves the OVS bridge task parameters.
func GetNetworkBridgeState(ctx context.Context, u uri.URI, s *NetworkBridgeState) error {
	op := operation.NewGet(ctx, u)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	if err := op.DecodeBody(s); err != nil {
		return err
	}

	return nil
}
