package lease

import (
	"errors"
	"fmt"
	"net"
	"slingshot/bmp"
	"slingshot/types"
	"sync"

	"github.com/golang/groupcache/singleflight"
)

// ManagerFactory creates and caches lease managers for subnets.
// Its cache uses the network interface index that is part of every dhcp
// request to map an incoming packet to a network address to a subnet in dcp.
type ManagerFactory struct {
	sync.Mutex
	singleflight.Group

	// ID for this service host
	id string

	// Map interface index to lease manager
	m map[int]*Manager
}

func NewManagerFactory(id string) *ManagerFactory {
	m := ManagerFactory{
		id: id,
	}

	return &m
}

func (m *ManagerFactory) findSubnetForIP(ip net.IP) (*types.Subnet, error) {
	subnets, err := bmp.NewClient().ListSubnets()
	if err != nil {
		return nil, err
	}

	for _, subnet := range subnets {
		if subnet.Subnet.Contains(ip) {
			return &subnet, nil
		}
	}

	return nil, fmt.Errorf("no subnet for ip: %s", ip)
}

func (m *ManagerFactory) findAddrForInterface(ifindex int) (net.IP, error) {
	intf, err := net.InterfaceByIndex(ifindex)
	if err != nil {
		return nil, err
	}

	a, err := intf.Addrs()
	if err != nil {
		return nil, err
	}

	// Use the first IPv4 address on receiving interface as server identifier.
	var ip net.IP
	for _, addr := range a {
		ip = addr.(*net.IPNet).IP.To4()
		if ip != nil {
			break
		}
	}

	if ip == nil {
		return nil, errors.New("no ipv4 address on interface")
	}

	return ip, nil
}

func (m *ManagerFactory) createManagerForIP(ifindex int, ip net.IP) func() (interface{}, error) {
	return func() (interface{}, error) {
		s, err := m.findSubnetForIP(ip)
		if err != nil {
			return nil, err
		}

		l := NewManager(m.id, s, ip)

		m.Lock()
		defer m.Unlock()

		if m.m == nil {
			m.m = make(map[int]*Manager)
		}

		m.m[ifindex] = l

		return l, nil
	}
}

func (m *ManagerFactory) LeaseManager(ifindex int) (*Manager, error) {
	m.Lock()
	l, ok := m.m[ifindex]
	m.Unlock()

	if !ok {
		ip, err := m.findAddrForInterface(ifindex)
		if err != nil {
			return nil, err
		}

		// Try to create lease manager for network this IP is attached to.
		// Use singleflight.Group to prevent concurrent routines from doing the same
		// thing for the same network.
		lif, err := m.Do(ip.String(), m.createManagerForIP(ifindex, ip))
		if err != nil {
			return nil, err
		}

		l = lif.(*Manager)
	}

	return l, nil
}
