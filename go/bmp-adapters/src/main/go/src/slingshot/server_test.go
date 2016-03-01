package slingshot
//
//import (
//	"net"
//	"runtime"
//	"testing"
//	"time"
//
//	"github.com/stretchr/testify/assert"
//	"github.com/stretchr/testify/suite"
//)
//
//type dhcpServerSuite struct {
//	suite.Suite
//
//	server  *Server
//	client  *net.UDPConn
//}
//
//var Timeout = 1 * time.Second
//
//func (suite *dhcpServerSuite) SetupTest() {
//	var err error
//	var addrToBroadcast *net.UDPAddr
//	var addrToListenTo *net.UDPAddr
//
//
//	addrToBroadcast, err = net.ResolveUDPAddr("udp4", "192.168.237.255:68")
//	addrToListenTo, err = net.ResolveUDPAddr("udp4", "0.0.0.0:67")
//	if err != nil {
//		panic(err)
//	}
//
//	s, err := MyNewServer("1", *addrToBroadcast, *addrToListenTo)
//	err = s.Run()
//	if !assert.NoError(suite.T(), err) {
//		return
//	}
//
//	suite.server = s
//}
//
//func (suite *dhcpServerSuite) TearDownTest() {
//	suite.server.Stop()
//	suite.client.Close()
//}
//
//func (suite *dhcpServerSuite) TestLoopDHCPServe() {
//	if suite.T().Failed() {
//		return
//	}
//
//	i := 0
//	for i < 2000 {
//		// Yield to allow the receiving routine to process this packet
//		time.Sleep(100 * time.Millisecond)
//		runtime.Gosched()
//	}
//}
//
//func TestServer(t *testing.T) {
//	suite.Run(t, new(dhcpServerSuite))
//}
