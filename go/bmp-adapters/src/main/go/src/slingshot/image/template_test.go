package image

import (
	"fmt"
	"io"
	"io/ioutil"
	"os"
	"slingshot/image/data"
	"slingshot/image/tftp/tftptest"
	"testing"
	"text/template"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

func TestTemplateMap(t *testing.T) {
	var tmpl *template.Template
	var err error

	h := &tftptest.Handler{}
	m := newTemplateMap(h)

	h.On("ReadFile", mock.Anything, "/ErrNotExist.tmpl").
		Return(nil, os.ErrNotExist)
	_, err = m.Find("/ErrNotExist")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrNotExist, err)
	}

	h.On("ReadFile", mock.Anything, "/ErrPermission.tmpl").
		Return(nil, os.ErrPermission)
	_, err = m.Find("/ErrPermission")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrPermission, err)
	}

	h.On("ReadFile", mock.Anything, "/ErrInvalid.tmpl").
		Return(tftptest.NewReadCloser("{{ invalid"), nil)
	_, err = m.Find("/ErrInvalid")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrInvalid, err)
	}

	h.On("ReadFile", mock.Anything, "/NoError.tmpl").
		Return(tftptest.NewReadCloser("this is a template"), nil)
	tmpl, err = m.Find("/NoError")
	assert.NoError(t, err)
	assert.NotNil(t, tmpl)
}

type templateData struct {
	Who  string
	What []string
}

func (t templateData) Error() (bool, error) {
	return true, fmt.Errorf("custom error")
}

func TestTemplateHandler(t *testing.T) {
	var rc io.ReadCloser
	var err error

	upstream := &tftptest.Handler{}
	m := newTemplateMap(upstream)
	h := m.Handler(templateData{Who: "world", What: []string{"thing1", "thing2"}})

	upstream.On("ReadFile", mock.Anything, "/hello.tmpl").Return(tftptest.NewReadCloser("Hello {{ .Who }}!"), nil)
	rc, err = h.ReadFile(gotftpd.ZeroConn, "/hello")
	if assert.NoError(t, err) {
		body, _ := ioutil.ReadAll(rc)
		assert.Equal(t, "Hello world!", string(body))
	}

	upstream.On("ReadFile", mock.Anything, "/foo.tmpl").Return(tftptest.NewReadCloser("elems {{ index .What 0 }} {{ index .What 1 }}"), nil)
	rc, err = h.ReadFile(gotftpd.ZeroConn, "/foo")
	if assert.NoError(t, err) {
		body, _ := ioutil.ReadAll(rc)
		assert.Equal(t, "elems thing1 thing2", string(body))
	}

	upstream.On("ReadFile", mock.Anything, "/error.tmpl").Return(tftptest.NewReadCloser("{{ .Error }}"), nil)
	_, err = h.ReadFile(gotftpd.ZeroConn, "/error")
	if assert.Error(t, err) {
		assert.Equal(t, os.ErrInvalid, err)
	}
}

func TestUserData(t *testing.T) {
	var rc io.ReadCloser
	var err error

	upstream := &tftptest.Handler{}
	m := newTemplateMap(upstream)

	d := &data.V1{ComputeStateReference: "bar", DiskStateReference : "-1"}
	h := m.Handler(d)

	upstream.On("ReadFile", mock.Anything, "/hello.tmpl").Return(tftptest.NewReadCloser("ComputeStateReference {{ .ComputeStateReference }} DiskStateReference {{ .DiskStateReference }}"), nil)
	rc, err = h.ReadFile(gotftpd.ZeroConn, "/hello")
	if assert.NoError(t, err) {
		body, _ := ioutil.ReadAll(rc)
		assert.Equal(t, "ComputeStateReference bar DiskStateReference -1", string(body))
	}
}
