package ipxe

import (
	"testing"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"

	"github.com/stretchr/testify/assert"
)

func TestOptionsNoClassMatch(t *testing.T) {
	o := make(dhcpv4.OptionMap)
	o.SetString(dhcpv4.OptionUserClass, "zPXE")

	_, err := LoadOptions(o)
	if assert.Error(t, err) {
		assert.Equal(t, ErrNoIPXEClient, err)
	}
}

func TestOptionsNoOption175(t *testing.T) {
	o := make(dhcpv4.OptionMap)
	o.SetString(dhcpv4.OptionUserClass, "iPXE")

	_, err := LoadOptions(o)
	if assert.Error(t, err) {
		assert.Equal(t, ErrNoOption175, err)
	}
}

func TestOptionsGarbageOption175(t *testing.T) {
	o := make(dhcpv4.OptionMap)
	o.SetString(dhcpv4.OptionUserClass, "iPXE")
	o.SetOption(OptionIPXEEncapsulation, []byte{0xfa, 0xfa, 0xfa, 0xfa})

	_, err := LoadOptions(o)
	if assert.Error(t, err) {
		assert.Equal(t, dhcpv4.ErrShortPacket, err)
	}
}

func TestOptionsValidOption175(t *testing.T) {
	// Example iPXE options comes from iPXE 1.0.0 DHCPDISCOVER packet
	b := []byte{
		0xb1, 0x05, 0x01, 0x80, 0x86, 0x10, 0x0e, // Option 177, BusID
		0x18, 0x01, 0x01, // Option 24, BzImage
		0x23, 0x01, 0x01, // Option 35, COMBOOT
		0x22, 0x01, 0x01, // Option 34, ELF
		0x19, 0x01, 0x01, // Option 25, Multiboot
		0x21, 0x01, 0x01, // Option 33, PXE
		0x10, 0x01, 0x02, // Option 16, PXEExt
		0x13, 0x01, 0x01, // Option 19, HTTP
		0x11, 0x01, 0x01, // Option 17, ISCSI
		0xeb, 0x03, 0x01, 0x00, 0x00, // Option 235, Version
		0x17, 0x01, 0x01, // Option 23, DNS
		0x15, 0x01, 0x01, // Option 21, TFTP
		0x12, 0x01, 0x01, // Option 18, AoE
	}

	o := make(dhcpv4.OptionMap)
	o.SetString(dhcpv4.OptionUserClass, "iPXE")
	o.SetOption(OptionIPXEEncapsulation, b)

	ipxe, err := LoadOptions(o)
	if assert.NoError(t, err) {
		assert.Equal(t, "\x01\x80\x86\x10\x0e", ipxe.BusID)
		assert.Equal(t, uint8(1), ipxe.BzImage)
		assert.Equal(t, uint8(1), ipxe.COMBOOT)
		assert.Equal(t, uint8(1), ipxe.ELF)
		assert.Equal(t, uint8(1), ipxe.Multiboot)
		assert.Equal(t, uint8(1), ipxe.PXE)
		assert.Equal(t, uint8(2), ipxe.PXEExt)
		assert.Equal(t, uint8(1), ipxe.HTTP)
		assert.Equal(t, uint8(1), ipxe.ISCSI)
		assert.Equal(t, "\x01\x00\x00", ipxe.Version)
		assert.Equal(t, uint8(1), ipxe.DNS)
		assert.Equal(t, uint8(1), ipxe.TFTP)
		assert.Equal(t, uint8(1), ipxe.AoE)
	}
}
