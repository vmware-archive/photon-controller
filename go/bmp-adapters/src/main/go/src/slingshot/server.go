package slingshot

import (
	"net"
	"slingshot/dhcp"
	"slingshot/image"
	"slingshot/lease"
	"time"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"
	"github.com/golang/glog"
)

type Server struct {
	imageHandler   *image.Handler
	imageServer    *image.Server
	dhcpServer     *dhcp.Server
	managerFactory *lease.ManagerFactory
}

func NewServer(id string) (*Server, error) {
	s := &Server{}
	s.imageHandler = image.NewHandler()
	s.imageServer = image.NewServer(net.IPv4zero, s.imageHandler)
	s.dhcpServer = dhcp.NewServer(net.UDPAddr{IP: net.IPv4zero, Port: 67}, s)
	s.managerFactory = lease.NewManagerFactory(id)

	return s, nil
}

func MyNewServer(id string, addrToBroadcast net.UDPAddr, addrToListenTo net.UDPAddr) (*Server, error) {
	s := &Server{}
	s.imageHandler = image.NewHandler()
	s.imageServer = image.NewServer(net.IPv4zero, s.imageHandler)
	s.dhcpServer = dhcp.MyNewServer(addrToBroadcast, addrToListenTo, s)
	s.managerFactory = lease.NewManagerFactory(id)

	return s, nil
}


func (s *Server) ServeRelease(req dhcpv4.Request) {
	// TODO(PN): Handle this...
}

func (s *Server) ServeInform(req dhcpv4.Request) {
	// TODO(PN): Handle this...
}

func (s *Server) ServeTxn(ch <-chan dhcpv4.Request) {
	var t *txnState
	var err error

	for done := false; !done; {
		var req dhcpv4.Request
		var rep dhcpv4.Reply

		// Receive next packet in this transaction
		select {
		case req = <-ch:
			// Good!
		case <-time.After(10 * time.Second):
			// Abort and ignore
			done = true
			continue
		}

		// Setup transaction state if this is the first packet in the transaction
		if t == nil {
			t, err = newTxn(s, req)
			if err != nil {
				glog.Infof("error creating lease: %s", err)
				return
			}
		}
		switch req.GetMessageType() {
		case dhcpv4.MessageTypeDHCPDiscover:
			rep = dhcpv4.CreateDHCPOffer(req)
		case dhcpv4.MessageTypeDHCPRequest:
			rep = dhcpv4.CreateDHCPAck(req)
		case dhcpv4.MessageTypeDHCPDecline:
			// Handle DHCPDECLINE
			continue
		default:
			// The client is expected to only send DISCOVER/REQUEST/DECLINE
			continue
		}

		err := t.WriteReply(req, rep)
		if err != nil {
			glog.Infof("error writing reply: %s", err)
			return
		}
	}
}

func (s *Server) Run() error {
	var err error

	err = s.imageServer.Run()
	if err != nil {
		return err
	}
	err = s.dhcpServer.Run()
	if err != nil {
		s.imageServer.Stop()
		return err
	}

	return nil
}

func (s *Server) Stop() {
	s.imageServer.Stop()
	s.dhcpServer.Stop()
}
