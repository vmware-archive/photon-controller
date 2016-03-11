package http

import (
	"net"
	"net/http"
	"sync"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
	"github.com/golang/glog"
)

func logHandler(handler http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		glog.Infof("%s %s %s", r.RemoteAddr, r.Method, r.URL)
		handler.ServeHTTP(w, r)
	})
}

type Server struct {
	gotftpd.Handler

	wg   sync.WaitGroup
	addr net.TCPAddr
	l    net.Listener
}

func NewServer(addr net.TCPAddr, h gotftpd.Handler) *Server {
	s := Server{
		Handler: h,
		addr:    addr,
	}

	// Make sure the IP in the address is IPv4
	s.addr.IP = s.addr.IP.To4()

	return &s
}

// Run starts the HTTP server, or returns an error when it cannot listen on the
// configured address.
func (s *Server) Run() error {
	glog.Infof("Http server is starting on %s", &s.addr)
	l, err := net.ListenTCP("tcp4", &s.addr)
	if err != nil {
		return err
	}

	s.l = l
	s.wg.Add(1)

	go func() {
		s.serve()
		s.wg.Done()
	}()

	return nil
}

// The tftp handler needs access to both the local address and the remote
// address. When the listener for this http server is listening on 0.0.0.0, the
// local address can be different depending on the network interface that a
// connection is accepted on.
//
// The local address of a connection is not available through net/http from the
// standard library. This is worked around by creating a one-off handler and a
// one-off mock listener for every connection.
//
func (s *Server) serve() error {
	defer s.l.Close()

	for {
		conn, err := s.l.Accept()
		if err != nil {
			return err
		}

		// Create custom handler for this connection
		h := Handler{
			Handler: s.Handler,

			local:  conn.LocalAddr().(*net.TCPAddr),
			remote: conn.RemoteAddr().(*net.TCPAddr),
		}

		go http.Serve(&fakeListener{conn}, logHandler(h))
	}
}

// Stop stops the HTTP server.
func (s *Server) Stop() {
	s.l.Close()
	s.wg.Wait()
}

// LocalAddr returns the address the server is bound to.
func (s *Server) LocalAddr() *net.TCPAddr {
	if s.l != nil {
		return s.l.Addr().(*net.TCPAddr)
	}
	return nil
}
