package dhcpv4

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

// Test dispatch to ReplyWriter
func TestDHCPDiscoverWriteReply(t *testing.T) {
	rw := &testReplyWriter{}

	req := DHCPDiscover{
		Packet:      NewPacket(BootRequest),
		ReplyWriter: rw,
	}

	reps := []Reply{
		CreateDHCPOffer(req),
	}

	for _, rep := range reps {
		rw.wrote = false
		req.WriteReply(rep)
		assert.True(t, rw.wrote)
	}
}
