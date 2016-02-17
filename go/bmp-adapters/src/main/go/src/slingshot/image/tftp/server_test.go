// +build linux

package tftp

import (
	"net"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestServer(t *testing.T) {
	var err error
	var addr *net.UDPAddr

	addr, err = net.ResolveUDPAddr("udp4", "127.0.0.1:0")
	if err != nil {
		panic(err)
	}

	s := NewServer(*addr, NewMuxer())
	err = s.Run()
	if !assert.NoError(t, err) {
		return
	}

	// The server should now be listening for packets on some ephemeral port,
	// find out which one.
	addr = s.LocalAddr()
	if !assert.NotNil(t, addr) {
		return
	}

	// Send a garbage packet to see that the other end is listening.
	// If it is not, the client will receive an ICMP Port Unreachable.
	client, err := net.DialUDP("udp4", nil, addr)
	if !assert.NoError(t, err) {
		return
	}

	client.SetWriteDeadline(time.Now().Add(100 * time.Millisecond))
	_, err = client.Write([]byte{0x1})
	assert.NoError(t, err)

	client.SetReadDeadline(time.Now().Add(100 * time.Millisecond))
	_, _, err = client.ReadFrom(make([]byte, 16))
	assert.NoError(t, err)

	s.Stop()
}
