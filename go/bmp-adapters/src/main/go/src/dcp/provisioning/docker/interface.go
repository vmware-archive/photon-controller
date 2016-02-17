package docker

import (
	"dcp"
	"dcp/provisioning/ssh"
	"fmt"
	"io"

	"github.com/golang/glog"
)

type SSHInterface struct {
	client   *ssh.Client
	netns    int
	portName string
}

// Wraps "ip" related commands around ssh.
func NewInterface(client *ssh.Client) *SSHInterface {
	return &SSHInterface{client: client}
}

// WithPort sets the port to modify
func (s *SSHInterface) WithPort(portName string) error {
	if portName == "" {
		return fmt.Errorf("portname can't be empty")
	}
	s.portName = portName
	return nil
}

func (s *SSHInterface) SetMTU(mtu int) error {
	setmtu := fmt.Sprintf("ip link set dev %s mtu %d", s.portName, mtu)
	err := s.run(setmtu, nil, nil)
	if err != nil {
		return fmt.Errorf("error setting mtu on %s: %s", s.portName, err)
	}
	return nil
}

func (s *SSHInterface) SetMac(mac string) error {
	// ip link set dev eth0 address 00:80:c8:f8:be:ef
	setMac := fmt.Sprintf("ip link set dev %s address %s", s.portName, mac)
	if err := s.run(setMac, nil, nil); err != nil {
		return fmt.Errorf("error setting mac on %s: %s", s.portName, err)
	}
	return nil
}

func (s *SSHInterface) SetIP(ip string, mask string) error {
	// ip addr add 10.1.1.1/32 dev A
	setIP := fmt.Sprintf("ip addr add %s/%s dev %s", ip, mask, s.portName)
	if err := s.run(setIP, nil, nil); err != nil {
		return fmt.Errorf("error setting ip on %s: %s", s.portName, err)
	}
	return nil
}

func (s *SSHInterface) SetGateway(gw string) error {
	return nil
}

func (s *SSHInterface) linkSet(state string) error {
	up := fmt.Sprintf("ip link set %s %s", s.portName, state)
	err := s.run(up, nil, nil)
	if err != nil {
		return fmt.Errorf("error setting interface %s %s: %s", s.portName, state, err)
	}
	return nil
}

func (s *SSHInterface) Up() error {
	return s.linkSet("up")
}

func (s *SSHInterface) Down() error {
	return s.linkSet("down")
}

func (s *SSHInterface) ChangeName(newName string) error {
	return nil
}

// WithNetns sets the netns the interface is in.  Since the commands are executed
// from the root context, ip needs to know where to find the interface.
func (s *SSHInterface) WithNetns(netns int) {
	s.netns = netns
}

// MoveNetns moves the given interface (associated to port on the ovs switch) to a
// different netns.
func (s *SSHInterface) MoveNetns(newNetns int) error {
	// sudo ip netns exec 887 ip link set ovs-f1d9bc1 netns 1184
	addToNs := fmt.Sprintf("ip link set %s netns %d", s.portName, newNetns)
	if err := s.run(addToNs, nil, nil); err != nil {
		return fmt.Errorf("error adding %s to netns %d: %s", s.portName, newNetns, err)
	}
	s.netns = newNetns
	return nil
}

func (s *SSHInterface) run(cmd string, stdout io.Writer, stdin io.Reader) error {
	c := fmt.Sprintf("sudo %s %s", s.netnsCmdPrefix(), cmd)

	if glog.V(dcp.Debug) {
		glog.Infof("cmd = %s", c)
	}
	return s.client.Run(c, stdout, stdin)
}

func (s *SSHInterface) netnsCmdPrefix() string {
	return fmt.Sprintf("ip netns exec %d", s.netns)
}
