package tftp

import (
	"os"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
)

type OverlayHandler []gotftpd.Handler

// ReadFile iterates over the underlying handlers and returns the values from
// the first one that returns a nil error or an error other than os.ErrExist
// (e.g. os.ErrPermission).
func (hs OverlayHandler) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	for _, h := range hs {
		rc, err := h.ReadFile(c, filename)
		if err != os.ErrNotExist {
			return rc, err
		}
	}

	// All handlers returned a not found error.
	return nil, os.ErrNotExist
}

// WriteFile always returns a permission error.
// It doesn't make sense to write to an overlay of handlers.
func (hs OverlayHandler) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	return nil, os.ErrPermission
}
