package dhcpv4

import (
	"net"

	"golang.org/x/net/ipv4"
)

// PacketReader defines an adaptation of the ReadFrom function (as defined
// net.PacketConn) that includes the interface index the packet arrived on.
type PacketReader interface {
	ReadFrom(b []byte) (n int, addr net.Addr, ifindex int, err error)
}

// PacketWriter defines an adaptation of the WriteTo function (as defined
// net.PacketConn) that includes the interface index the packet should be sent
// on.
type PacketWriter interface {
	WriteTo(b []byte, addr net.Addr, ifindex int) (n int, err error)
}

// PacketConn groups PacketReader and PacketWriter to form a subset of net.PacketConn.
type PacketConn interface {
	PacketReader
	PacketWriter

	Close() error
	LocalAddr() net.Addr
}

type replyWriter struct {
	pw PacketWriter

	// The client address, if any
	addr    net.UDPAddr

	// If bradcasting, override 255.255.255.255, if any
	addrBroadcastOverride    net.UDPAddr
	ifindex int
}

func (rw *replyWriter) WriteReply(r Reply) error {
	var err error

	err = r.Validate()
	if err != nil {
		return err
	}

	byteArray, err := r.ToBytes()
	if err != nil {
		return err
	}

	req := r.Request()
	addr := rw.addr
	bcast := req.GetFlags()[0] & 128

	// Broadcast the reply if the request packet has no address associated with
	// it, or if the client explicitly asks for a broadcast reply.
	if addr.IP.Equal(net.IPv4zero) || bcast > 0 {
		if rw.addrBroadcastOverride.IP.Equal(net.IPv4zero) {
			addr.IP = net.IPv4bcast
		} else {
			addr.IP = rw.addrBroadcastOverride.IP
		}
	}

	_, err = rw.pw.WriteTo(byteArray, &addr, rw.ifindex)
	if err != nil {
		return err
	}

	return nil
}

// Handler defines the interface an object needs to implement to handle DHCP
// packets. The handler should do a type switch on the Request object that is
// passed as argument to determine what kind of packet it is dealing with. It
// can use the WriteReply function on the request to send a reply back to the
// peer responsible for sending the request packet. While the handler may be
// blocking, it is not encouraged. Rather, the handler should return as soon as
// possible to avoid blocking the serve loop. If blocking operations need to be
// executed to determine if the request packet needs a reply, and if so, what
// kind of reply, it is recommended to handle this in separate goroutines. The
// WriteReply function can be called from multiple goroutines without needing
// extra synchronization.
type Handler interface {
	ServeDHCP(req Request)
}

// Serve reads packets off the network and calls the specified handler.
func Serve(pc PacketConn, addrBroadcastOverride *net.UDPAddr, h Handler) error {
	buf := make([]byte, 65536)

	for {
		n, addr, ifindex, err := pc.ReadFrom(buf)
		if err != nil {
			return err
		}

		p, err := PacketFromBytes(buf[:n])
		if err != nil {
			continue
		}

		// Stash interface index in packet structure
		p.ifindex = ifindex

		// Filter everything but requests
		if OpCode(p.Op()[0]) != BootRequest {
			continue
		}

		rw := replyWriter{
			pw: pc,

			addr:    *addr.(*net.UDPAddr),
			ifindex: ifindex,
		}

		if addrBroadcastOverride != nil {
			rw.addrBroadcastOverride = *addrBroadcastOverride
		}

		var req Request

		switch p.GetMessageType() {
		case MessageTypeDHCPDiscover:
			req = DHCPDiscover{p, &rw}
		case MessageTypeDHCPRequest:
			req = DHCPRequest{p, &rw}
		case MessageTypeDHCPDecline:
			req = DHCPDecline{p}
		case MessageTypeDHCPRelease:
			req = DHCPRelease{p}
		case MessageTypeDHCPInform:
			req = DHCPInform{p, &rw}
		}

		if req != nil {
			h.ServeDHCP(req)
		}
	}
}

type packetConn struct {
	net.PacketConn
	ipv4pc *ipv4.PacketConn
}

// NewPacketConn returns a PacketConn based on the specified net.PacketConn.
// It adds functionality to return the interface index from calls to ReadFrom
// and include the interface index argument in calls to WriteTo.
func NewPacketConn(pc net.PacketConn) (PacketConn, error) {
	ipv4pc := ipv4.NewPacketConn(pc)
	if err := ipv4pc.SetControlMessage(ipv4.FlagInterface, true); err != nil {
		return nil, err
	}

	p := packetConn{
		PacketConn: pc,
		ipv4pc:     ipv4pc,
	}

	return &p, nil
}

// ReadFrom reads a packet from the connection copying the payload into b. It
// returns the network interface index the packet arrived on in addition to the
// default return values of the ReadFrom function.
func (p *packetConn) ReadFrom(b []byte) (int, net.Addr, int, error) {
	n, cm, src, err := p.ipv4pc.ReadFrom(b)
	if err != nil {
		return n, src, -1, err
	}

	return n, src, cm.IfIndex, err
}

// WriteTo writes a packet with payload b to addr. It explicitly sends the
// packet over the network interface  with the specified index.
func (p *packetConn) WriteTo(b []byte, addr net.Addr, ifindex int) (int, error) {
	cm := &ipv4.ControlMessage{
		IfIndex: ifindex,
	}

	return p.ipv4pc.WriteTo(b, cm, addr)
}
