package image

import (
	"errors"
	"fmt"
	"net"
	"net/url"
	"os"
	"path"
	"regexp"
	"slingshot/image/data"
	"slingshot/types"
	"sync"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
	"github.com/golang/glog"
)

var (
	ErrNoImage = errors.New("no image configured")
)

type Handler struct {
	*boundHandler
}

func NewHandler() *Handler {
	h := Handler{
		boundHandler: newBoundHandler(),
	}

	return &h
}

var boundRegexp = regexp.MustCompile(`^/?([^/]+/[^/]+)(/.*)$`)

// boundHandler maintains images that are bound to a specific lease with a
// specific configuration. It is responsible for pruning idle bindings.
type boundHandler struct {
	sync.RWMutex

	m map[string]gotftpd.Handler
}

func newBoundHandler() *boundHandler {
	h := boundHandler{
		m: make(map[string]gotftpd.Handler),
	}

	return &h
}

func (h *boundHandler) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	msg := fmt.Sprintf("%s requesting %s", c.RemoteAddr(), filename)
	m := boundRegexp.FindStringSubmatch(filename)
	if m == nil {
		glog.Infof("%s error: bad match", msg)
		return nil, os.ErrNotExist
	}

	h.RLock()
	e := h.m["/"+m[1]]
	h.RUnlock()
	if e == nil {
		glog.Infof("%s error: no handler found", msg)
		return nil, os.ErrNotExist
	}

	r, err := e.ReadFile(c, m[2])
	if err != nil {
		glog.Infof("%s error: %s", msg, err)
		return nil, err
	}

	glog.Infof("%s", msg)
	return r, nil
}

func (h *boundHandler) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	return nil, os.ErrPermission
}

func imgPath(lease *types.Lease) string {
	return "/" + path.Join(lease.MAC.String(), lease.IP.IP.String())
}

type imageHandler interface {
	NextFile() string
	Handler(data interface{}) gotftpd.Handler
}

func (h *boundHandler) GetHandler(ih imageHandler, ip net.IP, s *types.Subnet, l *types.Lease, c *types.Configuration) (*data.V1, gotftpd.Handler) {
	d := data.V1{
		Host:     ip.String(),
		Path:     imgPath(l),
		MAC:      l.MAC.String(),
		IP:       l.IP.String(),
		Netmask:  net.IP(s.Subnet.Mask).String(),
		NextFile: ih.NextFile(),
		ComputeStateReference:     c.ComputeStateReference,
		DiskStateReference:     c.DiskStateReference,
	}

	for _, ip := range c.Routers {
		d.Routers = append(d.Routers, ip.String())
	}

	for _, ip := range c.NameServers {
		d.NameServers = append(d.NameServers, ip.String())
	}

	return &d, ih.Handler(d)
}

// Take a client's lease and configuration and add a static image mapping for
// this client to the handler. Any upstream changes to the subnet or the
// configuration will not have effect on this mapping to ensure consistency
// from the client's point of view (all requests for this mapping will use the
// same input data to templates).
func (h *boundHandler) Add(ip net.IP, s *types.Subnet, l *types.Lease, c *types.Configuration) (*data.V1, error) {
	if c.Image == "" {
		return nil, ErrNoImage
	}

	u, err := url.Parse(c.Image)
	if err != nil {
		return nil, err
	}

	// TODO(PN): This creates a new image for every client.
	img := NewImage(u)
	d, g := h.GetHandler(img, ip, s, l, c)
	msg := fmt.Sprintf("%s binding image %s", l.IP.String(), c.Image)

	h.Lock()
	h.m[d.Path] = g
	h.Unlock()

	glog.Infof("%s to %s", msg, d.Path)
	return d, nil
}

func (h *boundHandler) AddTar(ip net.IP, s *types.Subnet, l *types.Lease, c *types.Configuration, path string) (*data.V1, error) {
	// TODO(PN): This creates a new image for every client.
	img := NewTarImage(path)
	d, g := h.GetHandler(img, ip, s, l, c)
	msg := fmt.Sprintf("%s binding image %s", l.IP.String(), c.Image)

	h.Lock()
	h.m[d.Path] = g
	h.Unlock()

	glog.Infof("%s to %s", msg, d.Path)
	return d, nil
}

// Remove removes a path from handler map.
func (h *boundHandler) remove(path string) error {
	h.Lock()
	defer h.Unlock()

	_, ok := h.m[path]
	if !ok {
		return os.ErrNotExist
	}

	delete(h.m, path)
	return nil
}

// PurgeImage removes the associated image for the given lease.
func (h *boundHandler) PurgeImage(l *types.Lease) error {
	path := imgPath(l)
	if err := h.remove(path); err != nil {
		glog.Errorf("Error removing bound image (%s)", path)
	}

	return nil
}
