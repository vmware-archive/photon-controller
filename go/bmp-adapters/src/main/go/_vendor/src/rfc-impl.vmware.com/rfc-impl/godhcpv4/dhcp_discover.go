package dhcpv4

// DHCPDiscover is a client broadcast packet to locate available servers.
type DHCPDiscover struct {
	Packet
	ReplyWriter
}
