package ssh

import (
	"bytes"
	"dcp/client"
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/uri"
	"fmt"
	"net/http"
	"net/url"

	"golang.org/x/net/context"

	"github.com/golang/glog"
)

type CommandService struct {
	service host.Service
}

type ComputeCommandResponse struct {
	CommandResponse map[string]string `json:"commandResponse,omitempty"`
}

func NewCommandService() host.Service {
	s := &CommandService{}

	s.service = host.NewServiceContext(s)
	return s.service
}

func (s *CommandService) GetState() interface{} {
	return &common.ServiceDocument{}
}

func (s *CommandService) HandlePatch(ctx context.Context, op *operation.Operation) {
	req := &provisioning.ComputeCommandReference{}
	err := op.DecodeBody(req)
	if err != nil {
		glog.Errorf("Error decoding ComputeCommandRequest: %s", err)
		op.Fail(err)
		return
	}

	op.SetStatusCode(http.StatusCreated)
	op.Complete()

	ts := provisioning.NewTaskStateWrapper(ctx, req.ProvisioningTaskReference, provisioning.NewComputeSubTaskState())
	ts.PatchStage(common.TaskStageStarted)

	if req.IsMockRequest {
		// Simply PATCH task to completion and short-circuit the workflow.
		ts.PatchStage(common.TaskStageFinished)
		return
	}

	computeHost := &provisioning.ComputeStateWithDescription{}
	err = provisioning.GetComputeStateWithDescription(ctx, req.ComputeReference, computeHost)
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("Error getting ComputeState %s", req.ComputeReference), err)
		return
	}

	// Get the auth secrets
	creds := &host.AuthCredentialsServiceState{}
	authURI := uri.Extend(uri.Local(), computeHost.Description.AuthCredentialsLink)
	if err = host.GetAuthCredentialsServiceState(ctx, creds, authURI); err != nil {
		ts.PatchFailure(fmt.Sprintf("failed to get auth creds %s", authURI), err)
		return
	}

	sshClient, err := WithPublicKey(creds.UserEmail, creds.PrivateKey)
	if err != nil {
		ts.PatchFailure("failed to create ssh client", err)
		return
	}

	u, err := url.Parse(req.HostCommandReference)
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("url.Parse %s failed", req.HostCommandReference), err)
		return
	}

	err = sshClient.Connect(u.Host)
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("connect %s failed", req.HostCommandReference), err)
		return
	}

	defer sshClient.Close()
	commandOutput := make(map[string]string, len(req.Commands))
	for _, cmd := range req.Commands {
		var stdout bytes.Buffer
		err = sshClient.Run(cmd, &stdout, nil)
		commandOutput[cmd] = stdout.String()
		if err != nil {
			ts.PatchFailure(fmt.Sprintf("exec %s failed", cmd), err)
			return
		}
	}

	glog.Infof("Completed command request for %s", req.ComputeReference)

	response := ComputeCommandResponse{
		CommandResponse: commandOutput,
	}
	resultPatch := operation.NewPatch(ctx, req.ProvisioningTaskReference, nil).SetBody(response)
	err = client.Send(resultPatch).Wait()
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("patching compute result failed"), err)
		return
	}

	ts.PatchStage(common.TaskStageFinished)
}
