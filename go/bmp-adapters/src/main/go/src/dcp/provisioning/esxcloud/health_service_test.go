package esxcloud

import (
	"dcp/common/test"
	"dcp/provisioning"
	"testing"
)

func CheckHealthServiceCanStart(t *testing.T) {
	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.HealthServiceEsxCloud, NewInstanceService()); err != nil {
		t.Fatalf("Error starting ESX Cloud Health service: %s", err)
	}
}
