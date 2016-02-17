package dhcpv4

import "encoding/binary"

// DHCPOffer is a server to client packet in response to DHCPDISCOVER with
// offer of configuration parameters.
type DHCPOffer struct {
	Packet

	req Request
}

func CreateDHCPOffer(req Request) DHCPOffer {
	rep := DHCPOffer{
		Packet: NewReply(req),
		req:    req,
	}

	rep.SetMessageType(MessageTypeDHCPOffer)
	return rep
}

// From RFC2131, table 3:
//   Option                    DHCPOFFER
//   ------                    ---------
//   Requested IP address      MUST NOT
//   IP address lease time     MUST
//   Use 'file'/'sname' fields MAY
//   DHCP message type         DHCPOFFER
//   Parameter request list    MUST NOT
//   Message                   SHOULD
//   Client identifier         MUST NOT
//   Vendor class identifier   MAY
//   Server identifier         MUST
//   Maximum message size      MUST NOT
//   All others                MAY

var dhcpOfferValidation = []Validation{
	ValidateMustNot(OptionAddressRequest),
	ValidateMust(OptionAddressTime),
	ValidateMustNot(OptionParameterList),
	ValidateMustNot(OptionClientID),
	ValidateMust(OptionDHCPServerID),
	ValidateMustNot(OptionDHCPMaxMsgSize),
}

func (d DHCPOffer) Validate() error {
	return Validate(d.Packet, dhcpOfferValidation)
}

func (d DHCPOffer) ToBytes() ([]byte, error) {
	opts := packetToBytesOptions{}

	// Copy MaxMsgSize if set in the request
	if v, ok := d.Request().GetOption(OptionDHCPMaxMsgSize); ok {
		opts.maxLen = binary.BigEndian.Uint16(v)
	}

	return PacketToBytes(d.Packet, &opts)
}

func (d DHCPOffer) Request() Request {
	return d.req
}
