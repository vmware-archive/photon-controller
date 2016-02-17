package docker

import (
	"dcp/provisioning"
	"io/ioutil"
	"reflect"
	"testing"
)

func TestParseStats(t *testing.T) {
	b, err := ioutil.ReadFile("docker_stats_test.txt")
	if err != nil {
		t.Fatal(err)
	}

	health := &provisioning.ComputeHealthResponse{}

	err = parseStats(b, health)
	if err != nil {
		t.Error(err)
	}

	expect := &provisioning.ComputeHealthResponse{
		CPUCount:             0,
		CPUUtilizationMhz:    0,
		CPUTotalMhz:          0,
		CPUUtilizationPct:    0.07,
		TotalMemoryBytes:     1046000000,
		UsedMemoryBytes:      7098000,
		MemoryUtilizationPct: 0.68,
	}

	if !reflect.DeepEqual(expect, health) {
		t.Errorf("expected: %#v, got: %#v", expect, health)
	}
}
