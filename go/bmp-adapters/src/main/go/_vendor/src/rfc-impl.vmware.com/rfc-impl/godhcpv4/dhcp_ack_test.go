package dhcpv4

import "testing"

func TestDHCPAckOnRequestValidation(t *testing.T) {
	testCase := replyValidationTestCase{
		newReply: func() ValidatingReply {
			req := NewPacket(BootRequest)
			req.SetMessageType(MessageTypeDHCPRequest)
			return &DHCPAck{
				Packet: NewPacket(BootReply),
				req:    req,
			}
		},
		must: []Option{
			OptionAddressTime,
			OptionDHCPServerID,
		},
		mustNot: []Option{
			OptionAddressRequest,
			OptionParameterList,
			OptionClientID,
			OptionDHCPMaxMsgSize,
		},
	}

	testCase.Test(t)
}

func TestDHCPAckOnInformValidation(t *testing.T) {
	testCase := replyValidationTestCase{
		newReply: func() ValidatingReply {
			req := NewPacket(BootRequest)
			req.SetMessageType(MessageTypeDHCPInform)
			return &DHCPAck{
				Packet: NewPacket(BootReply),
				req:    req,
			}
		},
		must: []Option{
			OptionDHCPServerID,
		},
		mustNot: []Option{
			OptionAddressRequest,
			OptionAddressTime,
			OptionParameterList,
			OptionClientID,
			OptionDHCPMaxMsgSize,
		},
	}

	testCase.Test(t)
}
