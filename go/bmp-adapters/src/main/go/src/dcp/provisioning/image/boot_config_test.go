package image

import (
	"dcp/provisioning"
	"fmt"
	"io/ioutil"
	"path/filepath"
	"testing"

	"github.com/pborman/uuid"
)

func TestBootConfigRender(t *testing.T) {
	id := uuid.New()
	config := &provisioning.BootConfig{
		Label: "bootdisk",
		Data: map[string]string{
			"id": id,
		},
		Files: []provisioning.FileEntry{
			{
				Path:     "seed.txt.tmpl",
				Contents: "id={{ .id }}",
			},
		},
	}

	bootConfig, err := NewBootConfig(config)
	if err != nil {
		t.Fatal(err)
	}

	defer bootConfig.Cleanup()

	err = bootConfig.Process()
	if err != nil {
		t.Fatal(err)
	}

	buf, err := ioutil.ReadFile(filepath.Join(bootConfig.Dir, "seed.txt"))
	contents := string(buf)
	expect := fmt.Sprintf("id=%s", id)

	if contents != expect {
		t.Errorf("expected '%s', got: '%s'", expect, contents)
	}
}
