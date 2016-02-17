package pxe

import (
	"testing"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"

	"github.com/stretchr/testify/assert"
)

func pxeOptionsNewOptionMap() dhcpv4.OptionMap {
	o := make(dhcpv4.OptionMap)

	// This value is used by iPXE
	o.SetString(dhcpv4.OptionClassID, "PXEClient:Arch:00000:UNDI:002001")

	// Set defaults...
	o.SetUint16(dhcpv4.OptionClientSystem, 1)
	o.SetOption(dhcpv4.OptionClientNDI, []byte{0x1, 0x2, 0x1})
	o.SetOption(dhcpv4.OptionUUIDGUID, []byte{0x0, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1})

	return o
}

func TestOptionsNoClassMatch(t *testing.T) {
	o := make(dhcpv4.OptionMap)
	o.SetString(dhcpv4.OptionClassID, "I'm not a PXE client")

	_, err := LoadOptions(o)
	if assert.Error(t, err) {
		assert.Equal(t, ErrNoPXEClient, err)
	}
}

func TestOptionsSystemArchitectureType(t *testing.T) {
	var p Options
	var err error

	o := pxeOptionsNewOptionMap()
	delete(o, dhcpv4.OptionClientSystem)

	_, err = LoadOptions(o)
	if assert.Error(t, err) {
		assert.Equal(t, ErrNoPXEClient, err)
	}

	o.SetUint16(dhcpv4.OptionClientSystem, 1)

	p, err = LoadOptions(o)
	if assert.NoError(t, err) {
		assert.Equal(t, uint16(1), p.SystemArchitectureType)
	}
}

func TestOptionsNetworkInterfaceIdentifier(t *testing.T) {
	var p Options
	var err error

	o := pxeOptionsNewOptionMap()
	delete(o, dhcpv4.OptionClientNDI)

	_, err = LoadOptions(o)
	if assert.Error(t, err) {
		assert.Equal(t, ErrNoPXEClient, err)
	}

	v := []byte{0x1, 0x2, 0x1}
	o.SetOption(dhcpv4.OptionClientNDI, v)

	p, err = LoadOptions(o)
	if assert.NoError(t, err) {
		assert.Equal(t, [2]byte{0x2, 0x1}, p.NetworkInterfaceIdentifier)
	}
}

func TestOptionsMachineIdentifier(t *testing.T) {
	var p Options
	var err error

	o := pxeOptionsNewOptionMap()
	delete(o, dhcpv4.OptionUUIDGUID)

	_, err = LoadOptions(o)
	if assert.Error(t, err) {
		assert.Equal(t, ErrNoPXEClient, err)
	}

	v := []byte{0x0, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1, 0x1}
	o.SetOption(dhcpv4.OptionUUIDGUID, v)

	p, err = LoadOptions(o)
	if assert.NoError(t, err) {
		assert.Equal(t, v[1:], p.MachineIdentifier[:])
	}
}
