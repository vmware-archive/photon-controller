package ipxe

import (
	"errors"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"
)

var (
	ErrNoIPXEClient = errors.New("slingshot: not an iPXE client")
	ErrNoOption175  = errors.New("slingshot: expected option 175 to contain iPXE configuration")
)

const (
	OptionIPXEEncapsulation = dhcpv4.Option(175)
)

type Options struct {
	Priority        int8   `code:"1"`
	KeepSAN         uint8  `code:"8"`
	SkipSANBoot     uint8  `code:"9"`
	Syslogs         string `code:"85"`
	Cert            string `code:"91"`
	PrivKey         string `code:"92"`
	CrossCert       string `code:"93"`
	NoPXEDHCP       uint8  `code:"176"`
	BusID           string `code:"177"`
	BiosDrive       uint8  `code:"189"`
	Username        string `code:"190"`
	Password        string `code:"191"`
	ReverseUsername string `code:"192"`
	ReversePassword string `code:"193"`
	Version         string `code:"235"`

	// Feature indicators
	PXEExt    uint8 `code:"16"` // PXE API extensions
	ISCSI     uint8 `code:"17"` // iSCSI protocol
	AoE       uint8 `code:"18"` // AoE protocol
	HTTP      uint8 `code:"19"` // HTTP protocol
	HTTPS     uint8 `code:"20"` // HTTPS protocol
	TFTP      uint8 `code:"21"` // TFTP protocol
	FTP       uint8 `code:"22"` // FTP protocol
	DNS       uint8 `code:"23"` // DNS protocol
	BzImage   uint8 `code:"24"` // bzImage format
	Multiboot uint8 `code:"25"` // Multiboot format
	SLAM      uint8 `code:"26"` // SLAM protocol
	SRP       uint8 `code:"27"` // SRP protocol
	NBI       uint8 `code:"32"` // NBI format
	PXE       uint8 `code:"33"` // PXE format
	ELF       uint8 `code:"34"` // ELF format
	COMBOOT   uint8 `code:"35"` // COMBOOT format
	EFI       uint8 `code:"36"` // EFI format
	FCoE      uint8 `code:"37"` // FCoE protocol
	VLAN      uint8 `code:"38"` // VLAN support
	Menu      uint8 `code:"39"` // Menu support
	SDI       uint8 `code:"40"` // SDI image support
	NFS       uint8 `code:"41"` // NFS protocol
}

func LoadOptions(o dhcpv4.OptionGetter) (*Options, error) {
	var err error

	userClass, ok := o.GetString(dhcpv4.OptionUserClass)
	if !ok || userClass != "iPXE" {
		return nil, ErrNoIPXEClient
	}

	b, ok := o.GetOption(OptionIPXEEncapsulation)
	if !ok {
		return nil, ErrNoOption175
	}

	m := make(dhcpv4.OptionMap)
	mo := dhcpv4.OptionMapDeserializeOptions{
		IgnoreMissingEndTag: true,
	}

	// Parse encapsulated options
	err = m.Deserialize(b, &mo)
	if err != nil {
		return nil, err
	}

	ipxe := Options{}
	m.Decode(&ipxe)
	return &ipxe, nil
}
