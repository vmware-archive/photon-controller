package image

import (
	"fmt"
	"net/http"
	"net/url"
	"os"
	"path"
	"slingshot/image/tftp"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
)

type Image struct {
	u *url.URL
	t *templateMap
}

func NewImage(u *url.URL) *Image {
	img := Image{
		u: u,
	}

	img.t = newTemplateMap(&img)

	return &img
}

func (img *Image) URL(p string) string {
	u := *img.u
	u.Path = path.Join(u.Path, path.Clean(p))
	return u.String()
}

// NextFile returns the path local to the handler returned by the Handler
// function that should be used to let PXE clients know which file to request
// to continue booting.
func (img *Image) NextFile() string {
	// TODO(PN): Make configurable, or define some way to embed configuration
	// parameters like this (and maybe others) in the image itself at a
	// predefined path (maybe /cfg.json or something).
	return "ipxe"
}

// Handler returns a gotftpd.Handler implemented by overlaying the image's
// handler with the image's template handler, configured to use the specified
// `data' argument to execute any templates that may be served up.
func (img *Image) Handler(data interface{}) gotftpd.Handler {
	h := []gotftpd.Handler{
		img.t.Handler(data),
		img,
	}

	return tftp.OverlayHandler(h)
}

// ReadFile makes Image implement the gotftpd.Handler interface.
func (img *Image) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	req, err := http.NewRequest("GET", img.URL(filename), nil)
	if err != nil {
		return nil, err
	}

	res, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}

	switch res.StatusCode {
	case http.StatusOK:
		return gotftpd.ReadCloser(res.Body), nil
	case http.StatusNotFound:
		return nil, os.ErrNotExist
	}

	return nil, fmt.Errorf("http: %d (%s)", res.StatusCode, res.Status)
}

// WriteFile makes Image implement the gotftpd.Handler interface.
func (img *Image) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	return nil, os.ErrPermission
}
