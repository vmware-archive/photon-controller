package lease

import (
	"fmt"
	"net"
	"slingshot/bmp"
	"slingshot/types"
	"time"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"

	"github.com/golang/glog"
)

type Store interface {
	LoadConfiguration(id string, configuration *types.Configuration) error
	AcquireLease(subnetSelfLink string, mac types.MAC) (*types.Lease, error)
}

func ClientMAC(req dhcpv4.Request) types.MAC {
	return types.MAC{HardwareAddr: req.GetCHAddr()}
}

type Manager struct {
	store       Store
	subnet      *types.Subnet
	ip          net.IP // IP for the interface receiving the request.
	purgeExitCh chan bool

	// This servicehost's ID which can be used to compare the documentOwner
	// fields in ServiceDocuments.
	id string
}

var PurgeDuration = time.Duration(10 * time.Minute)

func NewManager(id string, subnet *types.Subnet, ip net.IP) *Manager {
	m := Manager{
		store:       bmp.NewClient(),
		subnet:      subnet,
		ip:          ip,
		id:          id,
		purgeExitCh: make(chan bool),
	}

	return &m
}

func (m *Manager) getConfiguration(mac *types.MAC) (*types.Configuration, error) {
	c := &types.Configuration{}

	// Load the subnet configuration
	err := m.store.LoadConfiguration(m.subnet.Subnet.String(), c)
	if err != nil {
		return nil, fmt.Errorf("error getting subnet (%s) configuration: %s", m.subnet.Subnet.String(), err)
	}

	// Set default
	c.LeaseTimeSec = 1200

	// Load the node specific configuration.  If no results are found, NOOP.
	// Use the configuration found above.
	_ = m.store.LoadConfiguration(mac.String(), c)

	return c, nil
}

func (m *Manager) Get(mac *types.MAC) (*types.Subnet, *types.Lease, *types.Configuration, error) {
	// Load configuration for this request
	configuration, err := m.getConfiguration(mac)
	if err != nil {
		return nil, nil, nil, err
	}

	// Check if DHCP is disabled per this machine's configuration
	if !configuration.Enabled {
		return nil, nil, nil, fmt.Errorf("dhcp disabled for %s", mac.String())
	}

	// Acquire lease by going through the subnet
	lease, err := m.store.AcquireLease(m.subnet.SelfLink, *mac)
	if err != nil {
		glog.Warningf("Error acquiring lease for %s: %s", mac.String(), err)
		return nil, nil, nil, err
	}

	return m.subnet, lease, configuration, nil
}

func (m *Manager) MyIP() net.IP {
	return m.ip
}
