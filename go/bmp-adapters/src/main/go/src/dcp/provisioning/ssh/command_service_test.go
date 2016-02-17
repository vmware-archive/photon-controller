package ssh

import (
	"dcp/client"
	"dcp/common"
	"dcp/common/test"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"fmt"
	"io"
	"testing"
	"time"

	"golang.org/x/net/context"
)

func TestCommandServiceWithMockRequest(t *testing.T) {
	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.SSHCommandService, NewCommandService()); err != nil {
		t.Fatalf("Error starting service: %s", err)
	}

	taskState := provisioning.NewComputeSubTaskState()
	taskState.TaskInfo.Stage = common.TaskStageCreated
	taskURI := th.StartMock(taskState)

	req := provisioning.ComputeCommandReference{
		ProvisioningTaskReference: taskURI,
		IsMockRequest:             true,
	}

	ctx := context.Background()
	op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.SSHCommandService), nil)
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

func TestCommandServiceWithRealRequest(t *testing.T) {
	t.SkipNow() // disabled until we implement the Java side (issue #41)

	th := test.NewServiceHost(t)
	defer th.Stop()

	if err := th.StartServiceSync(provisioning.SSHCommandService, NewCommandService()); err != nil {
		t.Fatalf("Error starting service: %s", err)
	}

	var cmds []string
	port := 0

	wg := test.StartSSHExecServer(&port, func(cmd string, _, _ io.Writer) int {
		cmds = append(cmds, cmd)
		return 0
	})

	auth := host.AuthCredentialsServiceState{
		UserEmail:  test.TestUser,
		PrivateKey: test.TestPrivateKey,
	}
	authURI := th.StartMock(auth)

	chsd := provisioning.ComputeStateWithDescription{
		Description: provisioning.ComputeDescription{
			AuthCredentialsLink: authURI.Path,
		},
	}
	chsURI := th.StartMock(chsd)

	taskState := provisioning.NewComputeSubTaskState()
	taskState.TaskInfo.Stage = common.TaskStageCreated
	taskURI := th.StartMock(taskState)

	testCommands := []string{"one", "two"}
	req := provisioning.ComputeCommandReference{
		ComputeReference:          chsURI,
		ProvisioningTaskReference: taskURI,
		Commands:                  testCommands,
		HostCommandReference:      fmt.Sprintf("ssh://localhost:%d", port),
		IsMockRequest:             false,
	}

	ctx := context.Background()
	op := operation.NewPatch(ctx, uri.Extend(th.URI(), provisioning.SSHCommandService), nil)
	if err := client.Send(op.SetBody(req)).Wait(); err != nil {
		t.Fatalf("Error issuing PATCH: %s", err)
	}

	err := test.WaitForTaskCompletion(time.Now().Add(time.Second), taskURI)
	if err != nil {
		t.Fatal(err)
	}

	wg.Wait()

	for i, cmd := range cmds {
		if cmd != testCommands[i] {
			t.Errorf("expected %s, got %s", testCommands[i], cmd)
		}
	}
}
