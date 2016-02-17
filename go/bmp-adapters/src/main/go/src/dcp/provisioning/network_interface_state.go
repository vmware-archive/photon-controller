package provisioning

import (
	"dcp/client"
	"dcp/common"
	"dcp/operation"
	"dcp/uri"
	"fmt"

	"golang.org/x/net/context"
)

type NetworkInterfaceState struct {
	common.ServiceDocument

	ID                     string `json:"id,omitempty"`
	NetworkDescriptionLink string `json:"networkDescriptionLink,omitempty"`
	LeaseLink              string `json:"leaseLink,omitempty"`
	Address                string `json:"address,omitempty"`
	NetworkBridgeLink      string `json:"networkBridgeLink,omitempty"`
}

// GetNetworkInterfaceState retrieves the referenced NetworkInterfaceState.
func GetNetworkInterfaceState(ctx context.Context, u uri.URI, s *NetworkInterfaceState) error {
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

// GetNetworkInterfaceState patches the referenced NetworkInterfaceState.
func PatchNetworkInterfaceState(ctx context.Context, u uri.URI, s *NetworkInterfaceState) error {
	op := operation.NewPatch(ctx, u, nil).SetBody(s)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	return nil
}

// PostNetworkInterfaceState posts the given disk state to the disk service.
func PostNetworkInterfaceState(ctx context.Context, intf *NetworkInterfaceState) (*NetworkInterfaceState, error) {
	u := uri.Extend(uri.Local(), NetworkInterfaces)
	op := operation.NewPost(ctx, u, nil)
	if err := client.Send(op.SetBody(intf)).Wait(); err != nil {
		return nil, fmt.Errorf("error POSTing %s: %s", u.String(), err)
	}

	out := &NetworkInterfaceState{}
	if err := op.DecodeBody(out); err != nil {
		return nil, err
	}

	return out, nil
}
