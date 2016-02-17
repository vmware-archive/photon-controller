package lease

import (
	"dcp/common"
	"slingshot/bmp"
	"slingshot/types"
	"testing"

	"github.com/stretchr/testify/assert"
)

type testManager struct {
	store   *bmp.TestStore
	manager *Manager
}

func setup() *testManager {
	t := &testManager{
		store: bmp.NewTestStore(),
	}

	t.manager = &Manager{
		store: t.store,
		id:    "test-id",
	}

	return t
}

// If no lease exists for a MAC, a new one should be created.  If a lease
// already exists for a mac, test it gets updated.
func TestUpdateCreateLease(t *testing.T) {
	m := setup()

	inSubnet := &types.Subnet{
		Subnet: types.NewIPNet("169.254.0.0/16"),
		Ranges: []types.Range{
			{Low: types.NewIP("169.254.0.10"),
				High: types.NewIP("169.254.0.20"),
			},
		},

		ServiceDocument: common.ServiceDocument{
			SelfLink: "169.254.0.0/16",
		},
	}
	m.manager.subnet = inSubnet

	_, err := m.store.CreateSubnet(*inSubnet)
	assert.NoError(t, err)

	conf := types.Configuration{
		Enabled: true,
	}

	m.store.Configurations[inSubnet.Subnet.String()] = conf

	inLease := types.Lease{
		MAC: types.NewMAC("f0:0d:00:42:1e:8e"),
		NetworkDescriptionLink: inSubnet.Subnet.String(),
	}

	m.store.Configurations[inLease.MAC.String()] = conf

	// no lease exists
	_, _, _, err = m.manager.Get(&inLease.MAC)
	assert.NoError(t, err)
	assert.NotEmpty(t, m.store.Leases)

	// check it gets updated.
	m.store.Leases[inLease.NetworkDescriptionLink][0].NetworkDescriptionLink = "10.0.0.0/8"

	var s *types.Subnet
	var l *types.Lease
	var c *types.Configuration
	s, l, c, err = m.manager.Get(&inLease.MAC)
	assert.NoError(t, err)
	assert.NotEmpty(t, l.IP)
	assert.Equal(t, s, inSubnet)
	assert.True(t, c.Enabled)
}
