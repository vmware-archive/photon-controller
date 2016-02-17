package image

import (
	"archive/tar"
	"io"
	"os"
	"path/filepath"
	"slingshot/image/tftp"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
)

type TarImage struct {
	path string
	t    *templateMap
}

func NewTarImage(path string) *TarImage {
	img := TarImage{
		path: path,
	}

	img.t = newTemplateMap(&img)

	return &img
}

func (img *TarImage) NextFile() string {
	return "ipxe"
}

func (img *TarImage) Handler(data interface{}) gotftpd.Handler {
	h := []gotftpd.Handler{
		img.t.Handler(data),
		img,
	}

	return tftp.OverlayHandler(h)
}

// ReadFile makes Image implement the gotftpd.Handler interface.
func (img *TarImage) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	rc, err := findFileInTar(img.path, filename)
	if err == io.EOF {
		return nil, os.ErrNotExist
	}

	return rc, err
}

// WriteFile makes Image implement the gotftpd.Handler interface.
func (img *TarImage) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	return nil, os.ErrPermission
}

type readCloser struct {
	io.Reader
	io.Closer
}

func findFileInTar(path string, filename string) (io.ReadCloser, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}

	r := tar.NewReader(f)
	for {
		h, err := r.Next()
		if err != nil {
			f.Close()
			return nil, err
		}

		// Treat paths in the archive as absolute
		h.Name = filepath.Clean("/" + h.Name)

		if h.Name == filename {
			// Read from archive reader, close underlying file.
			rc := readCloser{
				Reader: r,
				Closer: f,
			}

			return rc, nil
		}
	}
}
