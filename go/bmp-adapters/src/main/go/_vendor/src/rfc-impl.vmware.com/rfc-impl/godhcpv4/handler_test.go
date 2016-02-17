package dhcpv4

import (
	"errors"
	"io"
	"net"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

type testPacketConn struct {
	mock.Mock
}

func (pc *testPacketConn) ReadFrom(b []byte) (n int, addr net.Addr, ifindex int, err error) {
	args := pc.Called(b)

	switch arg0 := args.Get(0).(type) {
	case []byte:
		copy(b, arg0)
		n = len(arg0)
	}

	switch arg1 := args.Get(1).(type) {
	case net.Addr:
		addr = arg1
	default:
		addr = &net.UDPAddr{IP: net.IPv4zero, Port: 67}
	}

	switch arg2 := args.Get(2).(type) {
	case int:
		ifindex = arg2
	}

	switch arg3 := args.Get(3).(type) {
	case error:
		err = arg3
	}

	return n, addr, ifindex, err
}

func (pc *testPacketConn) WriteTo(b []byte, addr net.Addr, ifindex int) (n int, err error) {
	args := pc.Called(b, addr, ifindex)
	return args.Int(0), args.Error(1)
}

func (pc *testPacketConn) Close() error {
	return nil
}

func (pc *testPacketConn) LocalAddr() net.Addr {
	return &net.IPAddr{IP: net.IPv4zero}
}

func (pc *testPacketConn) ReadError(err error) {
	pc.On("ReadFrom", mock.Anything).Return(nil, nil, -1, io.EOF).Once()
}

func (pc *testPacketConn) ReadSuccess(b []byte) {
	pc.On("ReadFrom", mock.Anything).Return(b, nil, -1, nil).Once()
}

type testReply struct {
	mock.Mock

	// Embed OptionMap so this struct implements the Reply interface.
	OptionMap
}

func (r *testReply) Validate() error {
	args := r.Called()
	return args.Error(0)
}

func (r *testReply) ToBytes() (b []byte, err error) {
	args := r.Called()

	switch arg0 := args.Get(0).(type) {
	case []byte:
		b = arg0
	}

	switch arg1 := args.Get(1).(type) {
	case error:
		err = arg1
	}

	return b, err
}

func (r *testReply) Request() Request {
	args := r.Called()
	return args.Get(0).(Packet)
}

func (r *testReply) SetCIAddr(ip net.IP) {}
func (r *testReply) SetYIAddr(ip net.IP) {}
func (r *testReply) SetSIAddr(ip net.IP) {}
func (r *testReply) SetGIAddr(ip net.IP) {}

func TestReplyWriterReturnsValidationError(t *testing.T) {
	validationError := errors.New("some validation error")

	r := testReply{}
	r.On("Validate").Return(validationError)

	rw := replyWriter{
		pw: &testPacketConn{},
	}

	err := rw.WriteReply(&r)
	assert.Equal(t, validationError, err)
}

func TestReplyWriterReturnsSerializationError(t *testing.T) {
	serializationError := errors.New("some serialization error")

	r := testReply{}
	r.On("Validate").Return(nil)
	r.On("ToBytes").Return(nil, serializationError)

	rw := replyWriter{
		pw: &testPacketConn{},
	}

	err := rw.WriteReply(&r)
	assert.Equal(t, serializationError, err)
}

func TestReplyWriterDestinationAddress(t *testing.T) {
	withBcast := NewPacket(BootRequest)
	withBcast.Flags()[0] |= 128 // Set MSB

	withoutBcast := NewPacket(BootRequest)
	withoutBcast.Flags()[0] &= 127 // Clear MSB

	zeroIP := net.IP{0, 0, 0, 0}
	someIP := net.IP{1, 2, 3, 4}

	testCases := []struct {
		req Packet
		src net.UDPAddr
		dst net.IP
	}{
		// Broadcast flag trumps everything
		{withBcast, net.UDPAddr{IP: zeroIP}, net.IPv4bcast},
		{withBcast, net.UDPAddr{IP: someIP}, net.IPv4bcast},

		// Without broadcast flag, only broadcast without a destination IP
		{withoutBcast, net.UDPAddr{IP: zeroIP}, net.IPv4bcast},
		{withoutBcast, net.UDPAddr{IP: someIP}, someIP},
	}

	for _, testCase := range testCases {
		r := testReply{}
		r.On("Validate").Return(nil)
		r.On("ToBytes").Return([]byte("xyz"), nil)
		r.On("Request").Return(testCase.req)

		pw := &testPacketConn{}
		pw.On("WriteTo", mock.Anything, mock.Anything, mock.Anything).Return(3, nil)

		rw := replyWriter{
			pw:   pw,
			addr: testCase.src,
		}

		err := rw.WriteReply(&r)
		assert.NoError(t, err)

		expected := net.UDPAddr{IP: testCase.dst}
		actual := *pw.Calls[0].Arguments[1].(*net.UDPAddr)
		assert.Equal(t, expected, actual)
	}
}

type testHandler struct {
	mock.Mock
}

func (h *testHandler) ServeDHCP(req Request) {
	h.Called(req)
}

func TestServeReturnsReadError(t *testing.T) {
	pc := &testPacketConn{}
	pc.ReadError(io.EOF)

	err := Serve(pc, &testHandler{})
	assert.Equal(t, io.EOF, err)
}

func TestServeFiltersNonRequests(t *testing.T) {
	var err error
	var buf []byte
	var bufs [3][]byte

	bufs[0] = []byte("this is a garbage packet")

	p1 := NewPacket(OpCode(2)) // BootReply
	if buf, err = PacketToBytes(p1, nil); err != nil {
		panic(err)
	}

	bufs[1] = buf

	p2 := NewPacket(OpCode(2)) // Undefined opcode
	if buf, err = PacketToBytes(p2, nil); err != nil {
		panic(err)
	}

	bufs[2] = buf

	// Test that none of these buffers result in a call to ServeDHCP
	for _, buf := range bufs {
		pc := &testPacketConn{}
		pc.ReadSuccess(buf)
		pc.ReadError(io.EOF)

		h := &testHandler{}
		Serve(pc, h)

		h.AssertNotCalled(t, "ServeDHCP", mock.Anything)
	}
}

func TestServeRequestDispatch(t *testing.T) {
	testCases := []struct {
		t MessageType
		a mock.AnythingOfTypeArgument
	}{
		{MessageTypeDHCPDiscover, mock.AnythingOfType("DHCPDiscover")},
		{MessageTypeDHCPRequest, mock.AnythingOfType("DHCPRequest")},
		{MessageTypeDHCPDecline, mock.AnythingOfType("DHCPDecline")},
		{MessageTypeDHCPRelease, mock.AnythingOfType("DHCPRelease")},
		{MessageTypeDHCPInform, mock.AnythingOfType("DHCPInform")},
	}

	for _, testCase := range testCases {
		var buf []byte
		var err error

		p := NewPacket(BootRequest)
		p.SetMessageType(testCase.t)

		if buf, err = PacketToBytes(p, nil); err != nil {
			panic(err)
		}

		pc := &testPacketConn{}
		pc.ReadSuccess(buf)
		pc.ReadError(io.EOF)

		h := &testHandler{}
		h.On("ServeDHCP", mock.Anything).Return()

		Serve(pc, h)

		h.AssertCalled(t, "ServeDHCP", testCase.a)
	}
}
