package provisioning

import (
	"dcp/client"
	"dcp/operation"
	"dcp/uri"

	"golang.org/x/net/context"
)

type NetworkBridgeDescription struct {
	NetworkInstanceAdapterReference uri.URI `json:"networkInstanceAdapterReference"`
	ID                              string  `json:"id"`
}

// GetNetworkBridgeDescription retrieves the bridge description from the given URI.
func GetNetworkBridgeDescription(ctx context.Context, u uri.URI, s *NetworkBridgeDescription) error {
	u = uri.ExtendQuery(u, QueryExpand, QueryComputeDescriptionLink)
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
