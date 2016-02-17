package tftptest

import (
	"strings"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"

	"github.com/stretchr/testify/mock"
)

type Handler struct {
	mock.Mock
}

func (t *Handler) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	args := t.Called(c, filename)

	var rc gotftpd.ReadCloser
	var err error

	if len(args) > 0 {
		if v := args.Get(0); v != nil {
			rc = v.(gotftpd.ReadCloser)
		}

		if len(args) > 1 {
			if v := args.Get(1); v != nil {
				err = v.(error)
			}
		}
	}

	return rc, err
}

func (t *Handler) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	args := t.Called(c, filename)

	var wc gotftpd.WriteCloser
	var err error

	if len(args) > 0 {
		if v := args.Get(0); v != nil {
			wc = v.(gotftpd.WriteCloser)
		}

		if len(args) > 1 {
			if v := args.Get(1); v != nil {
				err = v.(error)
			}
		}
	}

	return wc, err
}

type ReadCloser struct{ *strings.Reader }

func (t ReadCloser) Close() error {
	return nil
}

func NewReadCloser(str string) ReadCloser {
	return ReadCloser{strings.NewReader(str)}
}
