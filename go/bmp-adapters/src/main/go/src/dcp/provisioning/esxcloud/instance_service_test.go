package esxcloud

import (
	"dcp/common/test"
	"dcp/provisioning"
	"testing"
)

func CheckInstanceServiceCanStart(t *testing.T) {
	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.InstanceServiceEsxCloud, NewInstanceService()); err != nil {
		t.Fatalf("Error starting ESX Cloud Instance service: %s", err)
	}
}
