package docker

import (
	"dcp/client"
	"dcp/common"
	"dcp/common/test"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"net/url"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"golang.org/x/net/context"

	"github.com/pborman/uuid"
)

const dcpTestImageURI = "https://enatai-jenkins.eng.vmware.com/job/docker-dcp-test/lastSuccessfulBuild/artifact/docker-dcp-test/dcp-test:latest.tgz"

func TestInstanceServiceCreateWithMockRequest(t *testing.T) {
	ctx := context.Background()
	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.InstanceServiceDocker, NewInstanceService()); err != nil {
		t.Fatalf("Error starting service: %s", err)
	}

	taskState := provisioning.NewComputeSubTaskState()
	taskState.TaskInfo.Stage = common.TaskStageCreated
	taskURI := th.StartMock(taskState)

	req := provisioning.ComputeInstanceRequest{
		ProvisioningTaskReference: taskURI,
		IsMockRequest:             true,
	}

	op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.InstanceServiceDocker), nil)
	if err := client.Send(op.SetBody(req)).Wait(); err != nil {
		t.Fatalf("Error issuing PATCH: %s", err)
	}

	err := test.WaitForTaskCompletion(time.Now().Add(time.Second), taskURI)
	if err != nil {
		t.Fatal(err)
	}

	if err != nil {
		t.Fatal(err)
	}
}

func TestInstanceServiceCreateWithRealRequest(t *testing.T) {
	ctx := context.Background()
	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.InstanceServiceDocker, NewInstanceService()); err != nil {
		t.Fatalf("Error starting service: %s", err)
	}

	const imageID = "0xcafebabe"
	const containerID = "0xdeadbeef"
	const containerIP = "172.17.0.153"
	var cmds []string
	port := 0

	runtimeInfo := make([]struct {
		NetworkSettings struct {
			IPAddress string
		}
	}, 1)

	runtimeInfo[0].NetworkSettings.IPAddress = containerIP

	wg := test.StartSSHExecServer(&port, func(cmd string, stderr, stdout io.Writer) int {
		cmds = append(cmds, cmd)
		switch {
		case strings.HasPrefix(cmd, "docker images"):
			io.WriteString(stdout, imageID+"\n")
		case strings.HasPrefix(cmd, "docker run"):
			io.WriteString(stdout, containerID+"\n")
		case strings.HasPrefix(cmd, "docker inspect"):
			b, _ := json.Marshal(runtimeInfo)
			stdout.Write(b)
		}
		return 0
	})

	sshPort = port

	auth := host.AuthCredentialsServiceState{
		UserEmail:  test.TestUser,
		PrivateKey: test.TestPrivateKey,
		Type:       host.AuthTypePublicKey,
	}
	authURI := th.StartMock(auth)

	app := struct {
		InternalIP string `json:"internal_ip"`
	}{}
	appURI := th.StartMock(app)

	registry := struct {
		Auth string `json:"auth"`
	}{}
	registryURI := th.StartMock(registry)
	registryHost := registryURI.Host
	authPath = registryURI.Path
	authScheme = "http"

	chsd := provisioning.ComputeStateWithDescription{
		ComputeState: provisioning.ComputeState{
			ID: uuid.New(),
		},
		Description: provisioning.ComputeDescription{
			AuthCredentialsLink: authURI.Path,
		},
	}
	chsURI := th.StartMock(&chsd)
	chsd.SelfLink = chsURI.Path

	parentChs := provisioning.ComputeStateWithDescription{
		ComputeState: provisioning.ComputeState{
			ID:      uuid.New(),
			Address: "127.0.0.1",
		},
	}
	parentChsURI := th.StartMock(&parentChs)
	chsd.ParentLink = parentChsURI.Path

	login := host.AuthCredentialsServiceState{
		UserEmail:    "enatai@vmware.com",
		PrivateKeyID: "the_username",
		PrivateKey:   "the_password",
	}
	loginURI := th.StartMock(login)

	testDisks := []*provisioning.DiskState{
		{
			DiskType:             provisioning.DiskTypeNetwork,
			SourceImageReference: fmt.Sprintf("docker://%s/dcp-test", registryHost),
			AuthCredentialsLink:  loginURI.Path,
			BootArguments: []string{
				"dcp-test:latest",
				"bin/run.sh",
				appURI.String(),
			},
		},
	}

	for _, diskState := range testDisks {
		diskURI := th.StartMock(diskState)
		diskState.SelfLink = diskURI.Path
		chsd.DiskLinks = append(chsd.DiskLinks, diskState.SelfLink)
	}

	taskState := provisioning.NewComputeSubTaskState()
	taskState.TaskInfo.Stage = common.TaskStageCreated
	taskURI := th.StartMock(taskState)

	req := &provisioning.ComputeInstanceRequest{
		ComputeReference:          chsURI,
		ProvisioningTaskReference: taskURI,
		RequestType:               provisioning.InstanceRequestTypeCreate,
	}

	op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.InstanceServiceDocker), nil)
	if err := client.Send(op.SetBody(req)).Wait(); err != nil {
		t.Fatalf("Error issuing PATCH: %s", err)
	}

	err := test.WaitForTaskCompletion(time.Now().Add(time.Second*5), taskURI)
	if err != nil {
		t.Fatal(err)
	}

	wg.Wait()

	for i, cmd := range cmds {
		t.Logf("ssh cmd %d=%s", i, cmd)
	}

	expect := []struct {
		name, expect, actual string
	}{
		{"Address", containerIP, chsd.Address},
		{"PowerState", provisioning.PowerStateOn, chsd.PowerState},
	}

	for _, e := range expect {
		if e.expect != e.actual {
			t.Errorf("expect %s==%s, got: '%s'", e.name, e.expect, e.actual)
		}
	}
}

// Test against coreos_production_vagrant_vmware_fusion.box
// We start a dcp-test container instance that phones home to
// this test (appURI) with its IP address
func TestInstanceServiceCreateWithCoreOS(t *testing.T) {
	ctx := context.Background()
	hostRef := os.Getenv("DCP_COREOS_HOST_REF")
	if hostRef == "" {
		t.SkipNow()
	}
	hostURI, err := url.Parse(hostRef)
	if err != nil {
		t.Fatal(err)
	}

	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.InstanceServiceDocker, NewInstanceService()); err != nil {
		t.Fatalf("Error starting service: %s", err)
	}

	auth := host.AuthCredentialsServiceState{
		UserEmail: "core",
		PrivateKey: `-----BEGIN RSA PRIVATE KEY-----
MIIEogIBAAKCAQEA6NF8iallvQVp22WDkTkyrtvp9eWW6A8YVr+kz4TjGYe7gHzI
w+niNltGEFHzD8+v1I2YJ6oXevct1YeS0o9HZyN1Q9qgCgzUFtdOKLv6IedplqoP
kcmF0aYet2PkEDo3MlTBckFXPITAMzF8dJSIFo9D8HfdOV0IAdx4O7PtixWKn5y2
hMNG0zQPyUecp4pzC6kivAIhyfHilFR61RGL+GPXQ2MWZWFYbAGjyiYJnAmCP3NO
Td0jMZEnDkbUvxhMmBYSdETk1rRgm+R4LOzFUGaHqHDLKLX+FIPKcF96hrucXzcW
yLbIbEgE98OHlnVYCzRdK8jlqm8tehUc9c9WhQIBIwKCAQEA4iqWPJXtzZA68mKd
ELs4jJsdyky+ewdZeNds5tjcnHU5zUYE25K+ffJED9qUWICcLZDc81TGWjHyAqD1
Bw7XpgUwFgeUJwUlzQurAv+/ySnxiwuaGJfhFM1CaQHzfXphgVml+fZUvnJUTvzf
TK2Lg6EdbUE9TarUlBf/xPfuEhMSlIE5keb/Zz3/LUlRg8yDqz5w+QWVJ4utnKnK
iqwZN0mwpwU7YSyJhlT4YV1F3n4YjLswM5wJs2oqm0jssQu/BT0tyEXNDYBLEF4A
sClaWuSJ2kjq7KhrrYXzagqhnSei9ODYFShJu8UWVec3Ihb5ZXlzO6vdNQ1J9Xsf
4m+2ywKBgQD6qFxx/Rv9CNN96l/4rb14HKirC2o/orApiHmHDsURs5rUKDx0f9iP
cXN7S1uePXuJRK/5hsubaOCx3Owd2u9gD6Oq0CsMkE4CUSiJcYrMANtx54cGH7Rk
EjFZxK8xAv1ldELEyxrFqkbE4BKd8QOt414qjvTGyAK+OLD3M2QdCQKBgQDtx8pN
CAxR7yhHbIWT1AH66+XWN8bXq7l3RO/ukeaci98JfkbkxURZhtxV/HHuvUhnPLdX
3TwygPBYZFNo4pzVEhzWoTtnEtrFueKxyc3+LjZpuo+mBlQ6ORtfgkr9gBVphXZG
YEzkCD3lVdl8L4cw9BVpKrJCs1c5taGjDgdInQKBgHm/fVvv96bJxc9x1tffXAcj
3OVdUN0UgXNCSaf/3A/phbeBQe9xS+3mpc4r6qvx+iy69mNBeNZ0xOitIjpjBo2+
dBEjSBwLk5q5tJqHmy/jKMJL4n9ROlx93XS+njxgibTvU6Fp9w+NOFD/HvxB3Tcz
6+jJF85D5BNAG3DBMKBjAoGBAOAxZvgsKN+JuENXsST7F89Tck2iTcQIT8g5rwWC
P9Vt74yboe2kDT531w8+egz7nAmRBKNM751U/95P9t88EDacDI/Z2OwnuFQHCPDF
llYOUI+SpLJ6/vURRbHSnnn8a/XG+nzedGH5JGqEJNQsz+xT2axM0/W/CRknmGaJ
kda/AoGANWrLCz708y7VYgAtW2Uf1DPOIYMdvo6fxIB5i9ZfISgcJ/bbCUkFrhoH
+vq/5CIWxCPp0f85R4qxxQ5ihxJ0YDQT9Jpx4TMss4PSavPaBH3RXow5Ohe+bYoQ
NE5OgEXk2wVfZczCZpigBKbKZHNYcelXtTt/nP3rsCuGcM4h53s=
-----END RSA PRIVATE KEY-----
`,
	}
	authURI := th.StartMock(auth)

	app := struct {
		InternalIP string `json:"internal_ip"`
	}{}
	appURI := th.StartMock(&app)
	appURI = uri.Extend(th.RemoteURI(hostURI.Host), appURI.Path)

	chsd := provisioning.ComputeStateWithDescription{
		ComputeState: provisioning.ComputeState{
			ID: uuid.New(),
			AdapterManagementReference: hostRef,
		},
		Description: provisioning.ComputeDescription{
			AuthCredentialsLink: authURI.Path,
		},
	}

	testDisks := []*provisioning.DiskState{
		{
			DiskType:             provisioning.DiskTypeNetwork,
			SourceImageReference: dcpTestImageURI,
			BootArguments: []string{
				"dcp-test:latest",
				"/opt/dcp/bin/dcp-test.sh",
				appURI.String(),
			},
		},
	}

	for _, diskState := range testDisks {
		diskURI := th.StartMock(diskState)
		diskState.SelfLink = diskURI.Path
		chsd.DiskLinks = append(chsd.DiskLinks, diskState.SelfLink)
	}

	chsURI := th.StartMock(&chsd)
	chsd.SelfLink = chsURI.Path

	address := strings.SplitN(hostURI.Host, ":", 2)
	if len(address) > 1 {
		sshPort, err = strconv.Atoi(address[1])
		if err != nil {
			t.Fatal(err)
		}
	} else {
		sshPort = 22
	}

	parentChs := provisioning.ComputeStateWithDescription{
		ComputeState: provisioning.ComputeState{
			ID:      uuid.New(),
			Address: address[0],
		},
	}
	parentChsURI := th.StartMock(&parentChs)
	chsd.ParentLink = parentChsURI.Path

	taskState := provisioning.NewComputeSubTaskState()
	taskState.TaskInfo.Stage = common.TaskStageCreated
	taskURI := th.StartMock(taskState)

	req := &provisioning.ComputeInstanceRequest{
		ComputeReference:          chsURI,
		ProvisioningTaskReference: taskURI,
		RequestType:               provisioning.InstanceRequestTypeCreate,
	}

	op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.InstanceServiceDocker), nil)
	if err := client.Send(op.SetBody(req)).Wait(); err != nil {
		t.Fatalf("Error issuing PATCH: %s", err)
	}

	err = test.WaitForTaskCompletion(time.Now().Add(time.Minute*30), taskURI)
	if err != nil {
		t.Fatal(err)
	}

	time.Sleep(time.Second * 5)
	if app.InternalIP == "" {
		t.Errorf("container failed to post IP")
	}

	if app.InternalIP != chsd.Address {
		t.Errorf("InternalIP %s != chsd.Address %s", app.InternalIP, chsd.Address)
	}

	for i := range testDisks {
		if testDisks[i].DiskStatus != provisioning.DiskStatusAttached {
			t.Errorf("DiskStatus %d was not PATCHed with DiskStatusAttached", i)
		}
	}

	req.RequestType = provisioning.InstanceRequestTypeDelete
	op = operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.InstanceServiceDocker), nil)
	if err := client.Send(op.SetBody(req)).Wait(); err != nil {
		t.Fatalf("Error issuing PATCH: %s", err)
	}

	err = test.WaitForTaskCompletion(time.Now().Add(time.Minute*30), taskURI)
	if err != nil {
		t.Fatal(err)
	}
}

func TestInspect(t *testing.T) {
	b, err := ioutil.ReadFile("docker_inspect_test.json")
	if err != nil {
		t.Fatal(err)
	}

	state := &provisioning.ComputeStateWithDescription{}
	err = dockerInspectState(b, state)
	if err != nil {
		t.Fatal(err)
	}

	if state.Address == "" {
		t.Error("empty Address")
	}

	if state.PrimaryMAC == "" {
		t.Error("empty PrimaryMAC")
	}

	if state.CustomProperties == nil {
		t.Error("empty CustomProperties")
	}

	if _, ok := state.CustomProperties[provisioning.CustomPropertyNameRuntimeInfo]; !ok {
		t.Errorf("empty CustomProperties[%s]", provisioning.CustomPropertyNameRuntimeInfo)
	}
}
