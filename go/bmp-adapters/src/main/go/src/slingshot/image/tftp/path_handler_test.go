package tftp

import (
	"os"
	"testing"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

type testOpener struct {
	mock.Mock
}

func (o *testOpener) OpenFile(name string, flag int, perm os.FileMode) (file *os.File, err error) {
	o.Called(name, flag, perm)
	return nil, nil
}

func TestPathHandler(t *testing.T) {
	var err error

	o := new(testOpener)

	h := PathHandler{
		Path:   "/foo/bar",
		Opener: o.OpenFile,
	}

	// Try to escape the root path
	_, err = h.ReadFile(gotftpd.ZeroConn, "../qux")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrPermission, err)
	}

	tests := []string{"./qux", "/qux", "qux"}

	o.On("OpenFile", "/foo/bar/qux", os.O_RDONLY, mock.Anything).Return()
	for _, p := range tests {
		_, err = h.ReadFile(gotftpd.ZeroConn, p)
		assert.NoError(t, err)
	}

	o.On("OpenFile", "/foo/bar/qux", os.O_WRONLY, mock.Anything).Return()
	for _, p := range tests {
		_, err = h.WriteFile(gotftpd.ZeroConn, p)
		assert.NoError(t, err)
	}
}
