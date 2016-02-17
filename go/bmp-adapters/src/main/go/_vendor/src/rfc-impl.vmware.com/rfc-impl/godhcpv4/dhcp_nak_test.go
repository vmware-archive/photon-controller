package dhcpv4

import "testing"

func TestDHCPNakValidation(t *testing.T) {
	testCase := replyValidationTestCase{
		newReply: func() ValidatingReply {
			return &DHCPNak{
				Packet: NewPacket(BootReply),
				req:    NewPacket(BootRequest),
			}
		},
		must: []Option{
			OptionDHCPServerID,
		},
		mustNot: []Option{
			OptionAddressRequest,
			OptionAddressTime,

			// Some random options that are not called out explicitly,
			// to test the deny-by-default policy.
			OptionPXEUndefined128,
			OptionPXEUndefined129,
		},
	}

	testCase.Test(t)
}
