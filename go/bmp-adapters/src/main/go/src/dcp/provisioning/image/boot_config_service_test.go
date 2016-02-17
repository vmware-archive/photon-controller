package image

import (
	"dcp/common/test"
	"dcp/provisioning"
	"dcp/uri"
	"io/ioutil"
	"os"
	"os/exec"
	"testing"

	"golang.org/x/net/context"

	"github.com/pborman/uuid"
)

func TestBootConfigServiceIso(t *testing.T) {
	// For testing on OSX:
	// brew install cdrtools
	// ln -s /usr/local/bin/mkisofs /usr/local/bin/genisoimage
	_, err := exec.LookPath("genisoimage")
	if err != nil {
		t.SkipNow()
	}

	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.BootConfigServiceIso, NewBootConfigService()); err != nil {
		t.Fatalf("Error starting service: %s", err)
	}

	computeState := &provisioning.ComputeState{
		ID: uuid.New(),
	}

	computeURI := th.StartMock(computeState)
	computeState.SelfLink = computeURI.Path

	diskState := &provisioning.DiskState{
		ID:       uuid.New(),
		DiskType: provisioning.DiskTypeCdrom,

		BootConfig: test.CoreosCloudConfig,

		CustomizationServiceReference: uri.Extend(uri.Local(), provisioning.BootConfigServiceIso).String(),
	}

	diskURI := th.StartMock(diskState)
	diskState.SelfLink = diskURI.Path

	ctx := context.Background()
	filePath, err := Download(ctx, computeState, diskState)
	if err != nil {
		t.Fatal(err)
	}

	defer os.Remove(filePath)

	b, err := ioutil.ReadFile(filePath)
	if err != nil {
		t.Fatal(err)
	}

	if chunk := string(b[0x8001:0x8006]); chunk != "CD001" {
		t.Errorf("Expected ISO prefix, got: %#v", chunk)
	}
}
