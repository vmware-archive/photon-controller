package provisioning

import (
	"dcp/client"
	"dcp/operation"
	"dcp/uri"
	"slingshot/types"

	"golang.org/x/net/context"
)

func AcquireLease(ctx context.Context, subnetURI uri.URI, mac types.MAC) (*types.Lease, error) {
	req := types.NewAcquireLeaseRequest(mac)
	op := operation.NewPatch(ctx, subnetURI, nil).SetBody(req)
	if err := client.Send(op).Wait(); err != nil {
		return nil, err
	}

	var out types.Lease
	if err := op.DecodeBody(&out); err != nil {
		return nil, err
	}
	return &out, nil
}

func PatchLease(ctx context.Context, lease *types.Lease) (*types.Lease, error) {
	leaseURI := uri.Extend(uri.Local(), lease.SelfLink)
	op := operation.NewPatch(ctx, leaseURI, nil).SetBody(lease)
	if err := client.Send(op).Wait(); err != nil {
		return nil, err
	}

	var out types.Lease
	if err := op.DecodeBody(&out); err != nil {
		return nil, err
	}

	return &out, nil
}
