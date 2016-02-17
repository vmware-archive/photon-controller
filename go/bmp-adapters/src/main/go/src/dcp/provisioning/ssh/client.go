package ssh

import (
	"bytes"
	"fmt"
	"io"
	"time"

	"dcp/host"
	"dcp/operation"

	"github.com/golang/glog"
	"golang.org/x/crypto/ssh"
	"golang.org/x/net/context"
)

const retryCount = 10

// Client is an ssh.Client wrapper
type Client struct {
	*ssh.Client
	config  *ssh.ClientConfig
	address string
}

func WithCredentials(creds *host.AuthCredentialsServiceState) (*Client, error) {
	authType := creds.Type
	if authType == "" {
		authType = host.AuthTypePublicKey
	}

	switch authType {
	case host.AuthTypePublicKey:
		return WithPublicKey(creds.UserEmail, creds.PrivateKey)
	case host.AuthTypePassword:
		return WithPassword(creds.UserEmail, creds.PrivateKey)
	default:
		return nil, fmt.Errorf("unsupported authentication type: %s", authType)
	}
}

// WithPublicKey creates a new Client configured to use
// public key authentication, using the given private key.
func WithPublicKey(user string, key string) (*Client, error) {
	signer, err := ssh.ParsePrivateKey([]byte(key))
	if err != nil {
		return nil, err
	}

	return &Client{
		config: &ssh.ClientConfig{
			User: user,
			Auth: []ssh.AuthMethod{
				ssh.PublicKeys(signer),
			},
		},
	}, nil
}

// WithPassword creates a new Client configured to use
// password authentication, using the given password.
func WithPassword(user string, password string) (*Client, error) {
	return &Client{
		config: &ssh.ClientConfig{
			User: user,
			Auth: []ssh.AuthMethod{
				ssh.Password(password),
			},
		},
	}, nil
}

// Connect to the given ssh address.
func (c *Client) Connect(address string) error {
	var err error
	var client *ssh.Client

	ctx := context.Background() // Operation only used for retry backoff
	op := operation.NewOperation(ctx).SetRetryCount(25)
	for {
		client, err = ssh.Dial("tcp", address, c.config)
		if err != nil {
			if op.DecrementRetriesRemaining() <= 0 {
				return err
			}

			op.WaitForRetryDelay(err)
			continue
		}

		c.address = address
		c.Client = client
		break
	}

	return err
}

// run command with retry
func (c *Client) RunWithRetry(cmd string, cmdOnFailure string, stdout io.Writer, stdin io.Reader) error {
	var err error
	attempt := 1
	for {
		err = c.Run(cmd, stdout, stdin)
		if err == nil {
			break
		}
		attempt++
		if attempt > retryCount {
			return fmt.Errorf("exceeded maximum number (%d) of retries for command: [%s]", retryCount, cmd)
		}
		if cmdOnFailure != "" {
			c.Run(cmdOnFailure, stdout, stdin)
		}
		// wait a second before retry
		time.Sleep(1 * time.Second)
	}
	return err
}

// Run the given list of commands over ssh.
func (c *Client) Run(cmd string, stdout io.Writer, stdin io.Reader) error {
	session, err := c.NewSession()
	if err != nil {
		return err
	}

	var stderr bytes.Buffer
	session.Stderr = &stderr
	session.Stdout = stdout
	session.Stdin = stdin

	err = session.Run(cmd)
	_ = session.Close()

	if err == nil {
		return nil
	}

	if e, ok := err.(*ssh.ExitError); ok {
		if e.Msg() != "" {
			return e
		}
		err = fmt.Errorf("process (%s) exited with: %v. stderr: %v",
			cmd, e.ExitStatus(), string(stderr.Bytes()))
	}

	glog.Errorf("ssh %s %s failed: %s", c.address, cmd, err)
	return err
}
