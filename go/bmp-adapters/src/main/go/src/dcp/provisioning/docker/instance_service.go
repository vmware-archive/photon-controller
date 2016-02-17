package docker

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"dcp/client"
	"dcp/common"
	"dcp/host"
	"dcp/operation"
	"dcp/provisioning"
	"dcp/provisioning/image"
	"dcp/provisioning/ssh"
	"dcp/uri"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"

	"golang.org/x/net/context"

	"github.com/golang/glog"
)

// This should be a const, but the tests change it
var sshPort = 22

const defaultOVSContainerName = "ovs"

type InstanceService struct {
	service host.Service
}

func NewInstanceService() host.Service {
	s := &InstanceService{}

	s.service = host.NewServiceContext(s)
	return s.service
}

func (s *InstanceService) GetState() interface{} {
	return &common.ServiceDocument{}
}

func (s *InstanceService) HandlePatch(ctx context.Context, op *operation.Operation) {
	req := &provisioning.ComputeInstanceRequest{}
	err := op.DecodeBody(req)
	if err != nil {
		glog.Errorf("Error decoding ComputeInstanceRequest: %s", err)
		op.Fail(err)
		return
	}

	op.SetStatusCode(http.StatusCreated)
	op.Complete()

	ts := provisioning.NewTaskStateWrapper(ctx, req.ProvisioningTaskReference, provisioning.NewComputeSubTaskState())
	ts.PatchStage(common.TaskStageStarted)

	if req.IsMockRequest {
		provisioning.HandleMockRequest(ctx, req, ts)
		return
	}

	state := &provisioning.ComputeStateWithDescription{}
	err = provisioning.GetComputeStateWithDescription(ctx, req.ComputeReference, state)
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("Error getting ComputeState %s", req.ComputeReference), err)
		return
	}

	client, err := sshConnect(ctx, state)
	if err != nil {
		ts.PatchFailure(fmt.Sprintf("Error connecting via ssh %s", req.ComputeReference), err)
		return
	}
	defer client.Close()

	switch req.RequestType {
	case provisioning.InstanceRequestTypeCreate:
		err = s.CreateInstance(ctx, client, state, req.ComputeReference, state.Address)
	case provisioning.InstanceRequestTypeDelete:
		err = s.DeleteInstance(ctx, client, state)
	default:
		ts.PatchFailure(fmt.Sprintf("%s error %s", req.ProvisioningTaskReference.String(), req.RequestType), provisioning.ErrUnsupportedRequestType)
		return
	}

	if err != nil {
		ts.PatchFailure(fmt.Sprintf("%s ID=%s", req.RequestType, state.ID), err)
		return
	}

	ts.PatchStage(common.TaskStageFinished)
	glog.Infof("%s request %s finished successfully.", req.RequestType, req.ComputeReference)
}

// get last non-empty line
// stdout may be captured from sshClient.RunWithRetry
// it may contain multiple lines because of retry and
// only the last line contains valid information
func getLastLine(stdout *bytes.Buffer) string {
	line := strings.TrimSpace(stdout.String())
	if pos := strings.LastIndex(line, "\n"); pos >= 0 {
		line = strings.TrimSpace(line[pos+1:])
	}
	return line
}

// findImageIDs extracts the docker image IDs from the repositories file.
// We can use these IDs to check if docker already the image(s) loaded,
// to avoid sending the image over the wire if we don't need to.
func findImageIDs(image string) ([]string, error) {
	var r *tar.Reader

	f, err := os.Open(image)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	if strings.HasSuffix(image, "gz") {
		gz, err := gzip.NewReader(f)
		if err != nil {
			return nil, err
		}
		defer gz.Close()

		r = tar.NewReader(gz)
	} else {
		r = tar.NewReader(f)
	}

	const fileName = "repositories"

	for {
		h, err := r.Next()
		if err != nil {
			if err == io.EOF {
				return nil, fmt.Errorf("'%s' not found in '%s'", fileName, image)
			}
			return nil, err
		}

		if h.Name == fileName {
			break
		}
	}

	repo := make(map[string]map[string]string)
	data, err := ioutil.ReadAll(r)
	if err != nil {
		return nil, err
	}

	err = json.Unmarshal(data, &repo)
	if err != nil {
		return nil, err
	}

	var ids []string
	for _, tags := range repo {
		for _, id := range tags {
			ids = append(ids, id)
		}
	}

	return ids, err
}

func (s *InstanceService) dockerLoad(ctx context.Context, client *ssh.Client, state *provisioning.ComputeState, disk *provisioning.DiskState) error {
	filePath, err := image.Download(ctx, state, disk)

	ids, err := findImageIDs(filePath)
	if err != nil {
		return err
	}
	var dockerInstance = ""
	dockerInstance, err = getDockerInstance(ctx, state)
	if err != nil {
		return err
	}

	inspect := fmt.Sprintf("%s inspect --format '{{.Id}}' %s", dockerInstance, strings.Join(ids, " "))
	err = client.RunWithRetry(inspect, "", nil, nil)
	if err == nil {
		// Image IDs are already loaded
		glog.Infof("docker images already loaded: %v", ids)
		return nil
	}

	f, err := os.Open(filePath)
	if err != nil {
		return err
	}
	defer f.Close()

	// TODO: Need to prevent parallel requests until docker PR 10888 is resolved.
	// https://github.com/docker/docker/pull/10888
	return client.RunWithRetry("docker load", "", nil, f)
}

var (
	authScheme = "https"
	authPath   = "/v1/auth"
)

func (s *InstanceService) dockerLogin(ctx context.Context, c *ssh.Client, auth *host.AuthCredentialsServiceState, host string) error {
	// 'docker login' command does not have an insecure option for use with self-signed certs
	u, err := uri.Parse(fmt.Sprintf("%s://%s%s", authScheme, host, authPath))
	if err != nil {
		return err
	}

	op := operation.NewGet(ctx, u).SetIsStreamResponse(true)
	req, err := op.CreateRequest()
	if err != nil {
		return err
	}

	req.SetBasicAuth(auth.PrivateKeyID, auth.PrivateKey)

	if err := client.Send(op).Wait(); err != nil {
		return err
	}

	body := op.GetResponse().Body

	// Set umask so .dockercfg is not readable by others and stream the auth response body to the file.
	// 'docker pull' commands use this configuration to authenticate with the registry.
	err = c.RunWithRetry(fmt.Sprintf("umask 077; cat > .dockercfg"), "", nil, body)

	_ = body.Close()

	return err
}

func (s *InstanceService) dockerPull(ctx context.Context, client *ssh.Client, state *provisioning.ComputeState, image string) error {
	var dockerInstance, err = getDockerInstance(ctx, state)
	if err != nil {
		return err
	}
	return client.RunWithRetry(fmt.Sprintf("%s pull %s", dockerInstance, image), "", nil, nil)
}

func dockerInspectState(buf []byte, state *provisioning.ComputeStateWithDescription) error {
	a := make([]map[string]interface{}, 1)
	err := json.Unmarshal(buf, &a)
	if err != nil {
		return err
	}

	// docker inspect can apply to multiple containers, we just have 1 in this case.
	if len(a) != 1 {
		return fmt.Errorf("invalid docker inspect format: '%s'", string(buf))
	}
	data := a[0]

	switch ns := data["NetworkSettings"].(type) {
	case map[string]interface{}:
		props := []struct {
			key string
			val *string
		}{
			{"IPAddress", &state.Address},
			{"MacAddress", &state.PrimaryMAC},
		}

		for _, prop := range props {
			if v, ok := ns[prop.key]; ok {
				if val, ok := v.(string); ok && val != "" {
					*prop.val = val
				}
			}
		}
	}

	state.CustomProperties = make(map[string]string)

	encodedData, err := json.Marshal(data)
	if err != nil {
		return err
	}

	state.CustomProperties[provisioning.CustomPropertyNameRuntimeInfo] = string(encodedData)

	return nil
}

func (s *InstanceService) dockerInspect(ctx context.Context, client *ssh.Client, state *provisioning.ComputeStateWithDescription, id string) error {
	var stdout bytes.Buffer
	var dockerInstance, err = getDockerInstance(ctx, &state.ComputeState)
	if err != nil {
		return err
	}

	err = client.RunWithRetry(fmt.Sprintf("%s inspect %s", dockerInstance, id), "", &stdout, nil)
	if err != nil {
		return err
	}

	return dockerInspectState(stdout.Bytes(), state)
}

func (s *InstanceService) CreateInstance(ctx context.Context, client *ssh.Client, state *provisioning.ComputeStateWithDescription, selfURI uri.URI, managementAddress string) error {
	diskStates, err := provisioning.GetDiskState(ctx, state.DiskLinks)
	if err != nil {
		return err
	}

	if len(diskStates) != 1 {
		return fmt.Errorf("expected single disk image, given %d", len(diskStates))
	}

	disk := diskStates[0]
	if disk.DiskType != provisioning.DiskTypeNetwork {
		return provisioning.ErrUnsupportedDiskType
	}

	u, err := url.Parse(disk.SourceImageReference)
	if err != nil {
		return err
	}

	var creds *host.AuthCredentialsServiceState
	if disk.AuthCredentialsLink != "" {
		creds = &host.AuthCredentialsServiceState{}
		authURI := uri.Extend(uri.Local(), disk.AuthCredentialsLink)

		if err = host.GetAuthCredentialsServiceState(ctx, creds, authURI); err != nil {
			return err
		}
	}

	// docker pull does not allow a scheme.
	// However, java.net.URI will fail to parse without a scheme: "hostname:port/repo"
	// We support the "docker" scheme to avoid this issue.
	if u.Scheme == "" || u.Scheme == "docker" {
		if creds != nil {
			if err := s.dockerLogin(ctx, client, creds, u.Host); err != nil {
				return err
			}
		}

		err = s.dockerPull(ctx, client, &state.ComputeState, u.Host+u.Path)
	} else {
		err = s.dockerLoad(ctx, client, &state.ComputeState, disk)
	}

	if err != nil {
		return err
	}

	var args []string
	for _, arg := range disk.BootArguments {
		args = append(args, arg)
	}

	var stdout bytes.Buffer
	var dockerInstance = ""
	dockerInstance, err = getDockerInstance(ctx, &state.ComputeState)
	if err != nil {
		return err
	}

	run := fmt.Sprintf("%s run --name %s -d %s", dockerInstance, state.ID, strings.Join(args, " "))
	// command to run on failure to invoke run
	remove := fmt.Sprintf("%s rm %s", dockerInstance, state.ID)
	err = client.RunWithRetry(run, remove, &stdout, nil)
	if err != nil {
		return err
	}

	id := getLastLine(&stdout)
	err = s.dockerInspect(ctx, client, state, id)
	if err != nil {
		return err
	}

	diskStates[0].DiskStatus = provisioning.DiskStatusAttached
	err = provisioning.PatchDiskState(ctx, diskStates)
	if err != nil {
		return err
	}

	patchBody := &provisioning.ComputeState{
		Address:          state.Address,
		PrimaryMAC:       state.PrimaryMAC,
		CustomProperties: state.CustomProperties,
		PowerState:       provisioning.PowerStateOn,
	}

	return provisioning.PatchComputeState(ctx, selfURI, patchBody)
}

func getDockerInstance(ctx context.Context, state *provisioning.ComputeState) (string, error) {
	diskStates, err := provisioning.GetDiskState(ctx, state.DiskLinks)
	if err != nil {
		return "", err
	}

	if len(diskStates) != 1 {
		return "", fmt.Errorf("expected single disk image, given %d", len(diskStates))
	}

	disk := diskStates[0]
	if disk.CustomProperties != nil {
		var dockerPort = disk.CustomProperties["DOCKER_SOCKET"]
		if dockerPort != "" {
			return fmt.Sprintf("docker -H %s", dockerPort), nil
		}
	}

	return "docker", nil
}

func (s *InstanceService) DeleteInstance(ctx context.Context, client *ssh.Client, state *provisioning.ComputeStateWithDescription) error {
	var dockerInstance, err = getDockerInstance(ctx, &state.ComputeState)
	if err != nil {
		return err
	}

	err = client.RunWithRetry(fmt.Sprintf("%s rm -fv %s", dockerInstance, state.ID), "", nil, nil)
	if err != nil {
		return err
	}

	return provisioning.DeleteComputeState(ctx, &state.ComputeState)
}

func getContainerPid(ctx context.Context, client *ssh.Client, state *provisioning.ComputeStateWithDescription, id string) (int, error) {
	var stdout bytes.Buffer
	var dockerInstance, err = getDockerInstance(ctx, &state.ComputeState)
	if err != nil {
		return 0, err
	}

	inspect := fmt.Sprintf("%s inspect --format '{{.State.Pid}}' %s", dockerInstance, id)
	err = client.RunWithRetry(inspect, "", &stdout, nil)
	if err != nil {
		return 0, err
	}

	pid, err := strconv.Atoi(getLastLine(&stdout))
	if err != nil {
		return 0, fmt.Errorf("error getting context pid: %s", err)
	}

	return pid, nil
}
