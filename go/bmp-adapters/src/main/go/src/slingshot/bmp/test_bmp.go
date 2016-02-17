package bmp

import "slingshot/types"

type TestStore struct {
	Leases         map[string][]*types.Lease
	Subnets        map[string]*types.Subnet
	Configurations map[string]types.Configuration
}

func NewTestStore() *TestStore {
	return &TestStore{
		Leases:         make(map[string][]*types.Lease),
		Subnets:        make(map[string]*types.Subnet),
		Configurations: make(map[string]types.Configuration),
	}
}

func (s *TestStore) ListLeases(subnetID string) ([]*types.Lease, error) {
	return s.Leases[subnetID], nil
}

func (s *TestStore) AcquireLease(subnetSelfLink string, mac types.MAC) (*types.Lease, error) {
	leases, ok := s.Leases[subnetSelfLink]
	if !ok {
		leases = make([]*types.Lease, 0)
	}

	// Find lease
	var lease *types.Lease
	for _, e := range leases {
		if e.MAC.Equal(mac) {
			lease = e
		}
	}

	// Not found
	if lease == nil {
		lease = &types.Lease{
			MAC: mac,
			NetworkDescriptionLink: subnetSelfLink,
		}

		leases = append(leases, lease)
		s.Leases[subnetSelfLink] = leases
	}

	return lease, nil
}

func (s *TestStore) LoadConfiguration(id string, configuration *types.Configuration) error {
	c, ok := s.Configurations[id]
	if ok {
		*configuration = c
	}
	return nil
}

// CreateSubnet creates a subnet.
func (s *TestStore) CreateSubnet(in types.Subnet) (*types.Subnet, error) {
	s.Subnets[in.Subnet.String()] = &in
	return &in, nil
}
