package ssh_test

import (
	"bytes"
	"fmt"
	"io"
	"strings"
	"testing"

	"dcp/common/test"
	"dcp/host"
	"dcp/provisioning/ssh"

	gossh "golang.org/x/crypto/ssh"
)

func TestClient(t *testing.T) {
	var cmds []string
	port := 0

	const cmdError = "xargs not allowed, write a service."
	const containerID = "0xdeadbeef"
	wg := test.StartSSHExecServer(&port, func(cmd string, stderr, stdout io.Writer) int {
		switch cmd {
		case "xargs":
			io.WriteString(stderr, cmdError)
			return 1
		case "docker run image":
			io.WriteString(stdout, containerID)
		}
		cmds = append(cmds, cmd)
		return 0
	})

	client, err := ssh.WithPublicKey(test.TestUser, test.TestPrivateKey)
	if err != nil {
		t.Fatal(err)
	}
	address := fmt.Sprintf("localhost:%d", port)

	err = client.Connect(address)
	if err != nil {
		t.Fatal(err)
	}

	var stdout bytes.Buffer
	expect := []string{"docker pull image", "docker run image"}

	for _, cmd := range expect {
		err = client.Run(cmd, &stdout, nil)
		if err != nil {
			t.Error(err)
		}
	}

	for i, cmd := range cmds {
		if cmd != expect[i] {
			t.Errorf("expected %s, got %s", expect[i], cmd)
		}
	}

	output := string(stdout.Bytes())
	if output != containerID {
		t.Errorf("expected %s, got '%s'", containerID, output)
	}

	err = client.Run("xargs", nil, nil)
	if err == nil {
		t.Error("expected error")
	} else {
		if !strings.Contains(err.Error(), cmdError) {
			t.Errorf("'%s' does not contain '%s'", err, cmdError)
		}
	}

	_ = client.Close()

	wg.Wait()
}

func TestAuthTypePassword(t *testing.T) {
	port := 0
	wg := test.StartSSHExecServer(&port, func(_ string, _, _ io.Writer) int {
		return 0
	})

	creds := &host.AuthCredentialsServiceState{
		UserEmail:  test.TestUser,
		PrivateKey: test.TestPassword,
		Type:       host.AuthTypePassword,
	}

	client, err := ssh.WithCredentials(creds)
	if err != nil {
		t.Fatal(err)
	}
	address := fmt.Sprintf("localhost:%d", port)

	err = client.Connect(address)
	if err != nil {
		t.Fatal(err)
	}

	_ = client.Close()

	wg.Wait()
}

func TestConnectRetry(t *testing.T) {
	defaultCertChecker := test.TestCertChecker

	client, err := ssh.WithPublicKey(test.TestUser, test.TestPrivateKey)
	if err != nil {
		t.Fatal(err)
	}

	called := false
	port := 0

	// clear the test cert checker so we get a failure on the first attempt:
	// "ssh: handshake failed: ssh: unable to authenticate, ..."
	test.TestCertChecker = gossh.CertChecker{}

	wg := test.StartSSHExecServer(&port, nil)

	go func() {
		wg.Wait()
		// Reset to default cert checker, so next attempt will succeed
		test.TestCertChecker = defaultCertChecker

		wg = test.StartSSHExecServer(&port, func(_ string, _, _ io.Writer) int {
			called = true
			return 0
		})
	}()

	address := fmt.Sprintf("localhost:%d", port)

	err = client.Connect(address)
	if err != nil {
		t.Fatal(err)
	}

	client.Run("echo foo", nil, nil)

	_ = client.Close()

	if called != true {
		t.Error("client did not send ssh server command")
	}
}
