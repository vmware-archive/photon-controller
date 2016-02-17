package tftp

import (
	"os"
	"slingshot/image/tftp/tftptest"
	"testing"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func TestMuxerAdd(t *testing.T) {
	var err error

	m := NewMuxer()
	h := &tftptest.Handler{}

	err = m.Add("/h", h)
	assert.NoError(t, err)

	for _, p := range []string{"/h", "h", "/h/whatever"} {
		err = m.Add(p, h)
		if assert.Error(t, err) {
			assert.Equal(t, ErrPathExists, err)
		}
	}
}

func TestMuxerHandle(t *testing.T) {
	var err error

	m := NewMuxer()
	h1 := &tftptest.Handler{}
	h2 := &tftptest.Handler{}

	m.Add("/h1", h1)
	m.Add("/h2", h2)

	// Dispatch to handler 1
	h1.On("ReadFile", mock.Anything, mock.Anything).Return()
	m.ReadFile(gotftpd.ZeroConn, "/h1/foo")
	h1.AssertCalled(t, "ReadFile", mock.Anything, "/foo")

	// Dispatch to handler 2
	h2.On("ReadFile", mock.Anything, mock.Anything).Return()
	m.ReadFile(gotftpd.ZeroConn, "/h2/bar")
	h2.AssertCalled(t, "ReadFile", mock.Anything, "/bar")

	// Dispatch to something that doesn't exist
	rc, err := m.ReadFile(gotftpd.ZeroConn, "/h3/foo")
	if assert.Nil(t, rc) && assert.Error(t, err) {
		assert.Equal(t, os.ErrNotExist, err)
	}
}
