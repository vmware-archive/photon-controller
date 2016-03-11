package dhcp

import (
	"errors"
	"fmt"
	"net"
	"sync"

	dhcpv4 "rfc-impl.vmware.com/rfc-impl/godhcpv4"
	"github.com/golang/glog"
)

type txnID string

type Handler interface {
	ServeTxn(ch <-chan dhcpv4.Request)
	ServeRelease(req dhcpv4.Request)
	ServeInform(req dhcpv4.Request)
}

type Server struct {
	txnLock sync.Mutex
	txnDone chan txnID
	txnMap  map[txnID]chan<- dhcpv4.Request

	// Map interface indices to server identifiers
	idLock sync.Mutex
	idMap  map[int]net.IP

	wg   sync.WaitGroup
	addr net.UDPAddr
	conn net.PacketConn
	h    Handler

	addrToListenTo net.UDPAddr
	// If any, the broadcast IP of network
	addressToBroadcast net.UDPAddr
}

type requestThatMayReply interface {
	dhcpv4.Request
	dhcpv4.ReplyWriter
}

type writeReplyWithServerID struct {
	requestThatMayReply
	ip net.IP
}

func (req writeReplyWithServerID) WriteReply(rep dhcpv4.Reply) error {
	rep.SetOption(dhcpv4.OptionDHCPServerID, req.ip.To4())
	return req.requestThatMayReply.WriteReply(rep)
}

// ClientID returns a request's client identifier. If the client ID option is
// specified, it is returned in its hexadecimal representation. Otherwise, it
// concatenates the request's HWType and CHAddr fields.
func ClientID(req dhcpv4.Request) string {
	if cid, ok := req.GetOption(dhcpv4.OptionClientID); ok && len(cid) > 0 {
		return fmt.Sprintf("%x", cid)
	}
	return fmt.Sprintf("%02x%x", req.GetHType(), req.GetCHAddr())
}

// generateTxnID generates a transaction ID off of the client ID
// and transaction ID.
func generateTxnID(req dhcpv4.Request) txnID {
	return txnID(fmt.Sprintf("%s-%s", ClientID(req), req.GetXID()))
}

// serverID returns the server identifier for the specified request.
// The server identifier is equal to the server's IP address. This server can
// serve clients on multiple interfaces and can have a different IP address on
// every interface. Therefore, the server identifier that is included in a DHCP
// reply depends on the network interface the request was received on.
func (d *Server) serverID(req dhcpv4.Request) (net.IP, error) {
	i := req.InterfaceIndex()
	if i <= 0 {
		return nil, errors.New("no such interface")
	}

	d.idLock.Lock()
	defer d.idLock.Unlock()

	ip, ok := d.idMap[i]
	if !ok {
		intf, err := net.InterfaceByIndex(i)
		if err != nil {
			return nil, err
		}

		a, err := intf.Addrs()
		if err != nil {
			return nil, err
		}

		// Use the first IPv4 address on receiving interface as server identifier.
		for _, addr := range a {
			ip = addr.(*net.IPNet).IP.To4()
			if ip != nil {
				break
			}
		}

		if ip == nil {
			return nil, errors.New("no IPv4 address on interface")
		}

		// Store IP for this interface
		if d.idMap == nil {
			d.idMap = make(map[int]net.IP)
		}
		d.idMap[i] = ip
	}

	return ip, nil
}

// ServeDHCP implements the dhcpv4 package packet handler. It
// dispatches every incoming request to the DHCP handler specific
// to slingshot. For DHCPDISCOVER requests, it either creates a
// new transaction or sends it to an existing one. It sends
// DHCPREQUEST and DHCPDECLINE packets to existing transactions.
// It dispatches DHCPRELEASE and DHCPINFORM requests separately.
func (d *Server) ServeDHCP(req dhcpv4.Request) {
	var allowNewTxn = false

	ip, err := d.serverID(req)
	if err != nil {
		return
	}

	// Ignore packets that specify a different server identifier
	serverID, ok := req.GetOption(dhcpv4.OptionDHCPServerID)
	if ok && ip.String() != net.IP(serverID).String() {
		return
	}

	// Include server identifier in replies if applicable
	if rtmr, ok := req.(requestThatMayReply); ok {
		req = writeReplyWithServerID{rtmr, ip}
	}

	// Dispatch requests that live outside transaction scope first
	switch req.GetMessageType() {
	case dhcpv4.MessageTypeDHCPDiscover:
		// Only allow creating a new transaction on discover request
		allowNewTxn = true
	case dhcpv4.MessageTypeDHCPRelease:
		go d.h.ServeRelease(req)
		return
	case dhcpv4.MessageTypeDHCPInform:
		go d.h.ServeInform(req)
		return
	}

	id := generateTxnID(req)

	d.txnLock.Lock()
	defer d.txnLock.Unlock()

	// Remove finished transactions from map
	{
		done := false
		for !done {
			select {
			case finishedTxnID := <-d.txnDone:
				delete(d.txnMap, finishedTxnID)
			default:
				done = true
			}
		}
	}

	// Create new transaction routine if necessary
	if _, ok := d.txnMap[id]; !ok {
		if !allowNewTxn {
			// Not allowed to create new transaction; drop request
			return
		}
		// The send below drops the request if it would block. Realistically we
		// only need a buffer of 1 here, but in the test suite we write a number of
		// packets sequentially instead of in a request/response interaction. To
		// help the tests out, give this channel a larger buffer.
		ch := make(chan dhcpv4.Request, 2)
		d.txnMap[id] = ch

		go func() {
			d.h.ServeTxn(ch)

			// This send may block, but that's OK
			d.txnDone <- id
		}()
	}

	// Post request to transaction routine, or drop it if this would block
	select {
	case d.txnMap[id] <- req:
	default:
	}
}

func NewServer(addr net.UDPAddr, h Handler) *Server {
	d := Server{
		txnDone: make(chan txnID),
		txnMap:  make(map[txnID]chan<- dhcpv4.Request),

		addr: addr,
		h:    h,
	}

	glog.Infof("Address passed to the new DHCP Server: %s", addr.IP.To4())
	// Make sure the IP in the address is IPv4
	d.addr.IP = d.addr.IP.To4()
	d.addrToListenTo = d.addr
	d.addressToBroadcast = net.UDPAddr{IP: d.addr.IP, Port: 255}
	glog.Infof("Adjusted addrToListenTo:%s - addr: %s -addrToBroadcast:%s",
		d.addrToListenTo.IP, d.addr.IP, d.addressToBroadcast.IP)

	return &d
}

func MyNewServer(addrToBroadcast net.UDPAddr, addrToListenTo net.UDPAddr,h Handler) *Server {
	d := Server{
		txnDone: make(chan txnID),
		txnMap:  make(map[txnID]chan<- dhcpv4.Request),

		addr: addrToListenTo,
		addressToBroadcast: addrToBroadcast,
		addrToListenTo: addrToListenTo,
		h:    h,
	}

	// Make sure the IP in the address is IPv4
	d.addrToListenTo.IP = d.addrToListenTo.IP.To4()
	d.addressToBroadcast.IP = d.addressToBroadcast.IP.To4()
	return &d
}

// Run starts the DHCP server, or returns an error when it cannot listen on the
// configured address.
func (d *Server) Run() error {
	var err error
	glog.Infof("DHCP server Run is called - addrToListenTo: %s - addr: %s - addrToBroadcast: %s", &d.addrToListenTo.IP,
		&d.addr.IP, &d.addressToBroadcast.IP)
	d.conn, err = net.ListenUDP("udp4", &d.addrToListenTo)

	if err != nil {
		return err
	}

	pconn, err := dhcpv4.NewPacketConn(d.conn)
	if err != nil {
		panic(err)
	}

	d.wg.Add(1)
	glog.Infof("DHCP server Run opened a connection and will serve")

	go func() {
		dhcpv4.Serve(pconn, &d.addressToBroadcast, d)
		d.wg.Done()
	}()
	return nil
}

// Stop stops the DHCP server.
func (d *Server) Stop() {
	d.conn.Close()
	d.wg.Wait()
}

// LocalAddr returns the address the server is bound to.
func (d *Server) LocalAddr() *net.UDPAddr {
	if d.conn != nil {
		return d.conn.LocalAddr().(*net.UDPAddr)
	}
	return nil
}
