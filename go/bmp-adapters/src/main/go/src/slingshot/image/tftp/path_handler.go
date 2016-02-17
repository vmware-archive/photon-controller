package tftp

import (
	"os"
	"path"
	"strings"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
)

type PathHandlerOpener func(string, int, os.FileMode) (*os.File, error)

type PathHandler struct {
	Path string

	// FileMode for files created by a write request
	FileMode os.FileMode
	Opener   PathHandlerOpener
}

func NewPathHandler(path string) PathHandler {
	h := PathHandler{
		Path: path,
	}

	return h
}

func (h PathHandler) open(filename string, flag int) (*os.File, error) {
	r := path.Clean(h.Path)
	s := path.Clean(path.Join(h.Path, filename))

	// The resulting path needs to have the root path as prefix to prevent escaping it
	if !strings.HasPrefix(s, r) {
		return nil, os.ErrPermission
	}

	o := h.Opener
	if o == nil {
		o = os.OpenFile
	}

	mode := h.FileMode
	if flag&os.O_WRONLY != 0 && mode == 0 {
		mode = 0644
	}

	f, err := o(s, flag, mode)

	// Normalize error
	if os.IsExist(err) {
		err = os.ErrExist
	} else if os.IsNotExist(err) {
		err = os.ErrNotExist
	} else if os.IsPermission(err) {
		err = os.ErrPermission
	}

	return f, err
}

func (h PathHandler) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	return h.open(filename, os.O_RDONLY)
}

func (h PathHandler) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	return h.open(filename, os.O_WRONLY)
}
