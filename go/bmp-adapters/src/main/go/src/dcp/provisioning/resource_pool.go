package provisioning

import (
	"dcp/client"
	"dcp/common"
	"dcp/operation"
	"dcp/uri"

	"golang.org/x/net/context"
)

const (
	PoolPropertyElastic = "ELASTIC"
	PoolPropertyHybrid  = "HYBRID"
)

type ResourcePoolState struct {
	common.ServiceDocument

	ID                   string            `json:"id,omitempty"`
	Name                 string            `json:"name,omitempty"`
	ProjectName          string            `json:"projectName,omitempty"`
	Properties           []string          `json:"properties,omitempty"`
	MinCPUCount          int64             `json:"minCpuCount,omitempty"`
	MaxCPUCount          int64             `json:"maxCpuCount,omitempty"`
	MinGpuCount          int64             `json:"minGpuCount,omitempty"`
	MaxGpuCount          int64             `json:"maxGpuCount,omitempty"`
	MinMemoryBytes       int64             `json:"minMemoryBytes,omitempty"`
	MaxMemoryBytes       int64             `json:"maxMemoryBytes,omitempty"`
	MinDiskCapacityBytes int64             `json:"minDiskCapacityBytes,omitempty"`
	MaxDiskCapacityBytes int64             `json:"maxDiskCapacityBytes,omitempty"`
	CustomProperties     map[string]string `json:"customProperties,omitempty"`
}

func GetResourcePoolState(ctx context.Context, p *ResourcePoolState, u uri.URI) error {
	op := operation.NewGet(ctx, u)
	err := client.Send(op).Wait()
	if err != nil {
		return err
	}

	if err := op.DecodeBody(p); err != nil {
		return err
	}

	return nil
}
