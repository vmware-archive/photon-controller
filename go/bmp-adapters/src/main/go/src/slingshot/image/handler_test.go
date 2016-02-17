package image

import (
	"net"
	"net/http"
	"os"
	"path"
	"slingshot/types"
	"sync"
	"testing"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"

	"github.com/stretchr/testify/assert"
)

type server struct {
	l net.Listener
	w sync.WaitGroup
	m map[string]string
}

func startServer() *server {
	var err error

	s := &server{
		m: make(map[string]string),
	}

	s.l, err = net.Listen("tcp4", "127.0.0.1:0")
	if err != nil {
		panic(err)
	}

	s.Run()

	return s
}

func (s *server) URL() string {
	return "http://" + s.l.Addr().String() + "/"
}

func (s *server) Run() {
	s.w.Add(1)
	go func() {
		http.Serve(s.l, s)
		s.w.Done()
	}()
}

func (s *server) Close() {
	s.l.Close()
	s.w.Wait()
}

func (s *server) Add(p string, body string) {
	s.m[p] = body
}

func (s *server) ServeHTTP(rw http.ResponseWriter, req *http.Request) {
	body, ok := s.m[req.URL.Path]
	if !ok {
		rw.WriteHeader(http.StatusNotFound)
		return
	}

	rw.Write([]byte(body))
}

func TestBoundHandler(t *testing.T) {
	s := startServer()
	defer s.Close()

	s.Add("/ipxe", "ipxe")

	// Bind image
	subnet := types.Subnet{}
	subnet.SelfLink = "/path/to/subnetID"
	subnet.Subnet = types.NewIPNet("127.0.0.1/8")

	lease := types.Lease{
		IP:  types.NewIP("127.0.0.2"),
		MAC: types.NewMAC("AA:BB:CC:DD:EE:FF"),
	}

	configuration := types.Configuration{
		Image: s.URL(),
	}

	h := newBoundHandler()

	//"/subnetID/127.0.0.2/ipxe"
	imgPath := "/" + path.Join(lease.MAC.String(), lease.IP.String(), "ipxe")

	// Image is not bound yet
	_, err := h.ReadFile(gotftpd.ZeroConn, imgPath)
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrNotExist, err)
	}

	_, err = h.Add(net.IPv4(127, 0, 0, 1), &subnet, &lease, &configuration)
	if !assert.NoError(t, err) {
		return
	}

	// Image is bound
	rc, err := h.ReadFile(gotftpd.ZeroConn, imgPath)
	if assert.NoError(t, err) {
		assert.NotNil(t, rc)
	}
}
