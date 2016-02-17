package dhcp

import (
	"encoding/binary"
	"net"
	"runtime"
	"sync"
	"testing"
	"time"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var Timeout = 1 * time.Second

type testHandler struct {
	serveTxn     func(ch <-chan dhcpv4.Request)
	serveRelease func(req dhcpv4.Request)
	serveInform  func(req dhcpv4.Request)
}

func (t *testHandler) ServeTxn(ch <-chan dhcpv4.Request) {
	if t.serveTxn != nil {
		t.serveTxn(ch)
	}
}

func (t *testHandler) ServeRelease(req dhcpv4.Request) {
	if t.serveRelease != nil {
		t.serveRelease(req)
	}
}

func (t *testHandler) ServeInform(req dhcpv4.Request) {
	if t.serveInform != nil {
		t.serveInform(req)
	}
}

type dhcpServerSuite struct {
	suite.Suite

	handler *testHandler
	server  *Server
	client  *net.UDPConn
}

func (suite *dhcpServerSuite) SetupTest() {
	var err error
	var addr *net.UDPAddr

	addr, err = net.ResolveUDPAddr("udp4", "127.0.0.1:0")
	if err != nil {
		panic(err)
	}

	h := &testHandler{}
	s := NewServer(*addr, h)
	err = s.Run()
	if !assert.NoError(suite.T(), err) {
		return
	}

	// The server should now be listening for packets on some ephemeral port,
	// find out which one.
	addr = s.LocalAddr()
	if !assert.NotNil(suite.T(), addr) {
		return
	}

	// Send a garbage packet to see that the other end is listening.
	// If it is not, the client will receive an ICMP Port Unreachable.
	client, err := net.DialUDP("udp4", nil, addr)
	if !assert.NoError(suite.T(), err) {
		return
	}

	suite.handler = h
	suite.server = s
	suite.client = client
}

func (suite *dhcpServerSuite) TearDownTest() {
	suite.server.Stop()
	suite.client.Close()
}

func fakeRequest(m dhcpv4.MessageType, xid uint32) dhcpv4.Packet {
	p := dhcpv4.NewPacket(dhcpv4.BootRequest)
	p.HType()[0] = 1 // Ethernet
	p.HLen()[0] = 6  // MAC-48 is 6 octets

	// Transaction ID
	binary.BigEndian.PutUint32(p.XID(), xid)

	// Client Hardware Address
	copy(p.CHAddr(), []byte{0xde, 0xad, 0xbe, 0xef, 0x00, 0x00})

	// DHCP Message Type option
	p.SetMessageType(m)

	return p
}

func fakeRequestBuffer(m dhcpv4.MessageType, xid uint32) []byte {
	buf, err := dhcpv4.PacketToBytes(fakeRequest(m, xid), nil)
	if err != nil {
		panic(err)
	}
	return buf
}

func (suite *dhcpServerSuite) TestNonTxn() {
	if suite.T().Failed() {
		return
	}

	releaseDone := make(chan byte)
	suite.handler.serveRelease = func(req dhcpv4.Request) {
		close(releaseDone)
	}

	informDone := make(chan byte)
	suite.handler.serveInform = func(req dhcpv4.Request) {
		close(informDone)
	}

	var tests = []struct {
		messageName string
		message     dhcpv4.MessageType
		doneCh      chan byte
	}{
		{
			messageName: "ServeRelease",
			message:     dhcpv4.MessageTypeDHCPRelease,
			doneCh:      releaseDone,
		},
		{
			messageName: "ServeInform",
			message:     dhcpv4.MessageTypeDHCPInform,
			doneCh:      informDone,
		},
	}

	for _, t := range tests {
		buf := fakeRequestBuffer(t.message, 1)
		_, err := suite.client.Write(buf)
		if !assert.NoError(suite.T(), err) {
			return
		}

		select {
		case <-t.doneCh:
		case <-time.After(Timeout):
			assert.Fail(suite.T(), "error timeout", "waiting for %s to be called", t.messageName)
			panic("timeout")
		}
	}
}

func (suite *dhcpServerSuite) TestTxn() {
	if suite.T().Failed() {
		return
	}

	lock := sync.Mutex{}
	count := make(map[uint32]int)
	done := make(chan byte)

	suite.handler.serveTxn = func(ch <-chan dhcpv4.Request) {
		for stop := false; !stop; {
			req := <-ch
			xidslice := req.GetXID()
			xid := binary.BigEndian.Uint32(xidslice[:])

			lock.Lock()

			if _, ok := count[xid]; ok {
				count[xid]--
				if count[xid] == 0 {
					delete(count, xid)

					// Notify test that all expected requests have been received
					if len(count) == 0 {
						close(done)
					}

					stop = true
				}
			} else {
				stop = true
			}

			lock.Unlock()
		}
	}

	var packets = []struct {
		m dhcpv4.MessageType
		x uint32
	}{
		{ // Should not be dispatched, transaction 3 was not started with a DHCPDISCOVER
			m: dhcpv4.MessageTypeDHCPRequest,
			x: 3,
		},
		{
			m: dhcpv4.MessageTypeDHCPDiscover,
			x: 1,
		},
		{
			m: dhcpv4.MessageTypeDHCPDiscover,
			x: 2,
		},
		{
			m: dhcpv4.MessageTypeDHCPRequest,
			x: 1,
		},
		{
			m: dhcpv4.MessageTypeDHCPRequest,
			x: 2,
		},
	}

	count[1] = 2
	count[2] = 2

	// Send requests
	for _, p := range packets {
		buf := fakeRequestBuffer(p.m, p.x)
		_, err := suite.client.Write(buf)
		if !assert.NoError(suite.T(), err) {
			return
		}
	}

	// Wait for ServeTxn to be done
	select {
	case <-done:
	case <-time.After(Timeout):
		assert.Fail(suite.T(), "timeout")
	}
}

func (suite *dhcpServerSuite) TestIgnoreRequestsWithInvalidServerID() {
	if suite.T().Failed() {
		return
	}

	ch := make(chan dhcpv4.Request)
	suite.handler.serveInform = func(req dhcpv4.Request) {
		ch <- req
	}

	serverIDs := []net.IP{
		net.ParseIP("127.0.0.3").To4(),
		net.ParseIP("127.0.0.2").To4(),
		net.ParseIP("127.0.0.1").To4(),
	}

	// Write packets with different server IDs
	for _, serverID := range serverIDs {
		req := fakeRequest(dhcpv4.MessageTypeDHCPInform, 1)
		req.SetOption(dhcpv4.OptionDHCPServerID, serverID)
		buf, err := dhcpv4.PacketToBytes(req, nil)
		if err != nil {
			panic(err)
		}

		_, err = suite.client.Write(buf)
		if !assert.NoError(suite.T(), err) {
			return
		}

		// Yield to allow the receiving routine to process this packet
		runtime.Gosched()
	}

	// Test that the packet that came through has a matching server ID
	req := <-ch
	serverID, ok := req.GetOption(dhcpv4.OptionDHCPServerID)
	if assert.True(suite.T(), ok) {
		assert.Equal(suite.T(), net.ParseIP("127.0.0.1").To4(), net.IP(serverID).To4())
	}
}

func (suite *dhcpServerSuite) TestIncludeServerIDInReply() {
	if suite.T().Failed() {
		return
	}

	suite.handler.serveInform = func(req dhcpv4.Request) {
		type replyWriter interface {
			WriteReply(rep dhcpv4.Reply) error
		}

		req.(replyWriter).WriteReply(dhcpv4.CreateDHCPAck(req))
	}

	// Write request
	{
		buf := fakeRequestBuffer(dhcpv4.MessageTypeDHCPInform, 1)
		_, err := suite.client.Write(buf)
		if !assert.NoError(suite.T(), err) {
			return
		}
	}

	// Read reply
	{
		buf := make([]byte, 1024)
		n, err := suite.client.Read(buf)
		if !assert.NoError(suite.T(), err) {
			return
		}

		p, err := dhcpv4.PacketFromBytes(buf[:n])
		if assert.NoError(suite.T(), err) {
			// Verify the server ID is set and correct
			serverID, ok := p.GetOption(dhcpv4.OptionDHCPServerID)
			if assert.True(suite.T(), ok) {
				assert.Equal(suite.T(), suite.server.addr.IP, net.IP(serverID).To4())
			}
		}
	}
}

func TestServer(t *testing.T) {
	suite.Run(t, new(dhcpServerSuite))
}
