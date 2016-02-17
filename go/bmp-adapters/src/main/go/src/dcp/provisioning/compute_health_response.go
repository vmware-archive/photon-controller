package provisioning

import "dcp/common"

const (
	HealthStateUnknown   = "UNKNOWN"
	HealthStateHealthy   = "HEALTHY"
	HealthStateUnhealthy = "UNHEALTHY"
)

type ComputeHealthResponse struct {
	common.ServiceDocument

	HealthState          string  `json:"healthState"`
	CPUCount             int64   `json:"cpuCount"`
	CPUUtilizationMhz    int64   `json:"cpuUtilizationMhz"`
	CPUTotalMhz          int64   `json:"cpuTotalMhz"`
	CPUUtilizationPct    float64 `json:"cpuUtilizationPct"`
	TotalMemoryBytes     int64   `json:"totalMemoryBytes"`
	UsedMemoryBytes      int64   `json:"usedMemoryBytes"`
	MemoryUtilizationPct float64 `json:"memoryUtilizationPct"`
}
