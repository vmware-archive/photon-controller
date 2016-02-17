package image

import (
	"net"
	"slingshot/image/http"
	"slingshot/image/tftp"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
)

type Server struct {
	tftpAddr net.UDPAddr
	tftp     *tftp.Server

	httpAddr net.TCPAddr
	http     *http.Server

	ip net.IP
	h  gotftpd.Handler
}

func NewServer(ip net.IP, h gotftpd.Handler) *Server {
	s := Server{
		ip: ip,
		h:  h,
	}

	s.tftpAddr = net.UDPAddr{IP: ip, Port: 69}
	s.tftp = tftp.NewServer(s.tftpAddr, h)

	s.httpAddr = net.TCPAddr{IP: ip, Port: 80}
	s.http = http.NewServer(s.httpAddr, h)

	return &s
}

func (s *Server) Run() error {
	var err error

	err = s.tftp.Run()
	if err != nil {
		return err
	}

	err = s.http.Run()
	if err != nil {
		s.tftp.Stop()
		return err
	}

	return nil
}

func (s *Server) Stop() {
	s.tftp.Stop()
	s.http.Stop()
}
