package docker

import (
	"dcp/host"
	"dcp/provisioning"
	"dcp/provisioning/ssh"
	"dcp/uri"
	"fmt"
	"net/url"

	"golang.org/x/net/context"
)

func sshConnect(ctx context.Context, state *provisioning.ComputeStateWithDescription) (*ssh.Client, error) {
	parentState := &provisioning.ComputeStateWithDescription{}
	parentComputeURI := uri.Extend(uri.Local(), state.ParentLink)
	if err := provisioning.GetComputeStateWithDescription(ctx, parentComputeURI, parentState); err != nil {
		return nil, fmt.Errorf("Error getting parent ComputeState %s: %s", parentComputeURI, err)
	}

	// Default to the parent container host's IP
	state.Address = parentState.Address

	creds := &host.AuthCredentialsServiceState{}
	authURI := uri.Extend(uri.Local(), state.Description.AuthCredentialsLink)
	if err := host.GetAuthCredentialsServiceState(ctx, creds, authURI); err != nil {
		return nil, fmt.Errorf("failed to get auth creds %s: %s", authURI, err)
	}

	client, err := ssh.WithCredentials(creds)
	if err != nil {
		return nil, fmt.Errorf("failed to create ssh client: %s", err)
	}

	managementReference := fmt.Sprintf("ssh://%s:%d", parentState.Address, sshPort)
	u, err := url.Parse(managementReference)
	if err != nil {
		return nil, fmt.Errorf("url.Parse %s failed: %s", managementReference, err)
	}

	err = client.Connect(u.Host)
	if err != nil {
		return nil, fmt.Errorf("connect %s failed: %s", managementReference, err)
	}

	return client, nil
}
