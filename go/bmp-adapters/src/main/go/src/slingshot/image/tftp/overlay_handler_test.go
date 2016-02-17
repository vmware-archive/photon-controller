package tftp

import (
	"os"
	"slingshot/image/tftp/tftptest"
	"testing"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func TestOverlayHandlerReadFile(t *testing.T) {
	var th = []*tftptest.Handler{
		&tftptest.Handler{},
		&tftptest.Handler{},
	}

	var h = OverlayHandler([]gotftpd.Handler{th[0], th[1]})
	var trc = tftptest.NewReadCloser("hello world")
	var rc gotftpd.ReadCloser
	var err error

	// Return OK from the first one
	th[0].On("ReadFile", mock.Anything, "/test1").Return(trc, nil)
	rc, err = h.ReadFile(gotftpd.ZeroConn, "/test1")
	if assert.NoError(t, err) {
		assert.Equal(t, trc, rc)
	}

	// Return error from the first one
	th[0].On("ReadFile", mock.Anything, "/test2").Return(nil, os.ErrPermission)
	_, err = h.ReadFile(gotftpd.ZeroConn, "/test2")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrPermission, err)
	}

	// Return OK from the second one
	th[0].On("ReadFile", mock.Anything, "/test3").Return(nil, os.ErrNotExist)
	th[1].On("ReadFile", mock.Anything, "/test3").Return(trc, nil)
	rc, err = h.ReadFile(gotftpd.ZeroConn, "/test3")
	if assert.NoError(t, err) {
		assert.Equal(t, trc, rc)
	}

	// Return error from the second one
	th[0].On("ReadFile", mock.Anything, "/test4").Return(nil, os.ErrNotExist)
	th[1].On("ReadFile", mock.Anything, "/test4").Return(nil, os.ErrPermission)
	_, err = h.ReadFile(gotftpd.ZeroConn, "/test4")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrPermission, err)
	}

	// Return from none of the handlers
	th[0].On("ReadFile", mock.Anything, "/test5").Return(nil, os.ErrNotExist)
	th[1].On("ReadFile", mock.Anything, "/test5").Return(nil, os.ErrNotExist)
	_, err = h.ReadFile(gotftpd.ZeroConn, "/test5")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrNotExist, err)
	}
}
