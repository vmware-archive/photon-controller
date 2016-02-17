package provisioning

import "dcp/uri"

type ComputeSnapshotRequest struct {
	SnapshotReference     uri.URI `json:"snapshotReference"`
	SnapshotTaskReference uri.URI `json:"snapshotTaskReference"`
	IsMockRequest         bool    `json:"isMockRequest"`
}
