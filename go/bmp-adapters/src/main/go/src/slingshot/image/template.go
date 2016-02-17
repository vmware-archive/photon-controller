package image

import (
	"bytes"
	"io/ioutil"
	"os"
	"path"
	"strings"
	"sync"
	"text/template"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"

	"github.com/golang/glog"
	"github.com/golang/groupcache/singleflight"
)

type templateEntry struct {
	tmpl *template.Template
	err  error
}

type templateMap struct {
	g  singleflight.Group
	mu sync.RWMutex
	m  map[string]templateEntry

	// Upstream handler
	handler gotftpd.Handler
	suffix  string
}

func newTemplateMap(handler gotftpd.Handler) *templateMap {
	t := templateMap{
		m: make(map[string]templateEntry),

		handler: handler,
		suffix:  ".tmpl",
	}

	return &t
}

func (t *templateMap) loadWithHandler(path string) (*template.Template, error) {
	rc, err := t.handler.ReadFile(gotftpd.ZeroConn, path+t.suffix)
	if err != nil {
		return nil, err
	}

	input, err := ioutil.ReadAll(rc)
	if err != nil {
		return nil, err
	}

	rc.Close()

	tmpl := template.New(path)

	funcMap := template.FuncMap{
		"ToUpper": strings.ToUpper,
		"ToLower": strings.ToLower,
		"Replace": strings.Replace,
		"Join":    strings.Join,
	}

	tmpl.Funcs(funcMap)

	tmpl, err = tmpl.Parse(string(input))
	if err != nil {
		glog.Infof("parse error: %s", err)

		// Return os.ErrInvalid to cache the parse failure
		return nil, os.ErrInvalid
	}

	return tmpl, nil
}

// Find looks up the parsed template for the specified path. If the template
// map does not have an entry for the specified path, it tries to read the
// template from its upstream handler. If an error occurs while doing so, this
// error is cached and returned on subsequent calls for the same path.
func (t *templateMap) Find(path string) (*template.Template, error) {
	ti, err := t.g.Do(path, func() (interface{}, error) {
		t.mu.RLock()
		e, ok := t.m[path]
		t.mu.RUnlock()

		if !ok {
			tmpl, err := t.loadWithHandler(path)
			e = templateEntry{tmpl, err}

			// Only store errors:
			//  - nil:           all good
			//  - os.ErrExist:   template does not exist
			//  - os.ErrInvalid: template failed to parse
			switch err {
			case nil, os.ErrExist, os.ErrInvalid:
				t.mu.Lock()
				t.m[path] = e
				t.mu.Unlock()
			}
		}

		return e.tmpl, e.err
	})

	return ti.(*template.Template), err
}

func (t *templateMap) Handler(data interface{}) gotftpd.Handler {
	h := templateHandler{
		t:    t,
		data: data,
	}

	return h
}

type closableBuffer struct{ bytes.Buffer }

// Close never returns an error.
// Its sole purpose is to make a buffer adhere to the io.ReadCloser interface.
func (c *closableBuffer) Close() error {
	return nil
}

type templateHandler struct {
	t    *templateMap
	data interface{}
}

func (h templateHandler) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	tmpl, err := h.t.Find(path.Clean(filename))
	if err != nil {
		return nil, err
	}

	b := closableBuffer{}
	err = tmpl.Execute(&b, h.data)
	if err != nil {
		glog.Infof("execution error for %s: %s", c.RemoteAddr(), err)
		return nil, os.ErrInvalid
	}

	return &b, nil
}

func (h templateHandler) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	return nil, os.ErrPermission
}
