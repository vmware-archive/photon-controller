package pxe

import (
	"errors"
	"strings"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"
)

var (
	ErrNoPXEClient = errors.New("slingshot: not a PXE client")
)

// System Architecture constants (see RFC4578 section 2.1)
const (
	SystemArchitectureIntelX86PC      = 0
	SystemArchitectureNECPC98         = 1
	SystemArchitectureEFIItanium      = 2
	SystemArchitectureDECAlpha        = 3
	SystemArchitectureArcX86          = 4
	SystemArchitectureIntelLeanClient = 5
	SystemArchitectureEFIIA32         = 6
	SystemArchitectureEFIBC           = 7
	SystemArchitectureEFIXscale       = 8
	SystemArchitectureEFIX8664        = 9
)

var systemArchitectureStrings = map[int]string{
	SystemArchitectureIntelX86PC:      "Intel x86PC",
	SystemArchitectureNECPC98:         "NEC/PC98",
	SystemArchitectureEFIItanium:      "EFI Itanium",
	SystemArchitectureDECAlpha:        "DEC Alpha",
	SystemArchitectureArcX86:          "Arc x86",
	SystemArchitectureIntelLeanClient: "Intel Lean Client",
	SystemArchitectureEFIIA32:         "EFI IA32",
	SystemArchitectureEFIBC:           "EFI BC",
	SystemArchitectureEFIXscale:       "EFI Xscale",
	SystemArchitectureEFIX8664:        "EFI x86-64",
}

// Options holds the PXE option values retrieved from a request packet.
type Options struct {
	SystemArchitectureType     uint16
	NetworkInterfaceIdentifier [2]uint8
	MachineIdentifier          [16]uint8
}

func LoadOptions(o dhcpv4.OptionGetter) (Options, error) {
	p := Options{}

	classID, ok := o.GetString(dhcpv4.OptionClassID)
	if !ok || !strings.HasPrefix(classID, "PXEClient") {
		return p, ErrNoPXEClient
	}

	{
		v, ok := o.GetUint16(dhcpv4.OptionClientSystem)
		if !ok {
			return Options{}, ErrNoPXEClient
		}

		// See RFC4578, section 2.1
		p.SystemArchitectureType = v
	}

	{
		v, ok := o.GetOption(dhcpv4.OptionClientNDI)
		if !(ok && len(v) == 3) {
			return Options{}, ErrNoPXEClient
		}

		// See RFC4578, section 2.2
		copy(p.NetworkInterfaceIdentifier[0:2], v[1:3])
	}

	{
		v, ok := o.GetOption(dhcpv4.OptionUUIDGUID)
		if !(ok && len(v) == 17) {
			return Options{}, ErrNoPXEClient
		}

		// See RFC4578, section 2.3
		copy(p.MachineIdentifier[0:16], v[1:17])
	}

	return p, nil
}
