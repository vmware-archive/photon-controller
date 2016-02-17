package tftp

import (
	"net"
	"sync"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
)

type Server struct {
	gotftpd.Handler

	wg   sync.WaitGroup
	addr net.UDPAddr
	conn net.PacketConn
}

func NewServer(addr net.UDPAddr, h gotftpd.Handler) *Server {
	t := Server{
		Handler: h,
		addr:    addr,
	}

	// Make sure the IP in the address is IPv4
	t.addr.IP = t.addr.IP.To4()

	return &t
}

// Run starts the TFTP server, or returns an error when it cannot listen on the
// configured address.
func (t *Server) Run() error {
	conn, err := net.ListenUDP("udp4", &t.addr)

	if err != nil {
		return err
	}

	t.conn = conn
	t.wg.Add(1)

	go func() {
		gotftpd.Serve(conn, t)
		t.wg.Done()
	}()

	return nil
}

// Stop stops the TFTP server. Because UDP is stateless, this will abort any
// in-flight read or write sequences.
func (t *Server) Stop() {
	t.conn.Close()
	t.wg.Wait()
}

// LocalAddr returns the address the server is bound to.
func (t *Server) LocalAddr() *net.UDPAddr {
	if t.conn != nil {
		return t.conn.LocalAddr().(*net.UDPAddr)
	}
	return nil
}
