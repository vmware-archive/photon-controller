package tftp

import (
	"errors"
	"os"
	"path"
	"strings"
	"sync"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
)

var (
	ErrInvalidPath = errors.New("slingshot: tftp mux invalid path")
	ErrPathExists  = errors.New("slingshot: tftp mux path exists")
	ErrNoPath      = errors.New("slingshot: tftp mux path does not exist")
)

type Muxer struct {
	mu sync.RWMutex
	m  map[string]gotftpd.Handler
}

func NewMuxer() *Muxer {
	t := Muxer{
		m: make(map[string]gotftpd.Handler),
	}

	return &t
}

// splitPath splits a path into a head and tail component.
// For example, if you pass "/foo/bar", it will return "foo" as head and "/bar"
// as tail. If you pass "foo/bar", it will do the same. If you pass "/foo", it
// will return "foo" with an empty tail.
func splitPath(p string) (string, string) {
	p = path.Clean(p)

	// Remove leading / if present
	if len(p) > 0 && p[0] == '/' {
		p = p[1:]
	}

	// Find first /
	i := strings.Index(p, "/")
	if i == -1 {
		return p, ""
	}

	return p[:i], p[i:]
}

// Add adds a TFTP handler to the muxer at specified name. It can not deal with
// full paths (e.g. like /foo/bar) to keep it O(1). If you need multiple paths,
// use nested muxers.
func (t *Muxer) Add(path string, h gotftpd.Handler) error {
	head, _ := splitPath(path)
	if len(head) == 0 {
		return ErrInvalidPath
	}

	t.mu.Lock()
	defer t.mu.Unlock()

	// Don't allow duplicates
	if _, ok := t.m[head]; ok {
		return ErrPathExists
	}

	// Set handler
	t.m[head] = h
	return nil
}

func (t *Muxer) findHandler(head string) (gotftpd.Handler, error) {
	t.mu.RLock()
	h, ok := t.m[head]
	t.mu.RUnlock()

	if !ok {
		return nil, os.ErrNotExist
	}

	return h, nil
}

func (t *Muxer) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	head, tail := splitPath(filename)
	h, err := t.findHandler(head)
	if err != nil {
		return nil, err
	}

	return h.ReadFile(c, tail)
}

func (t *Muxer) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	head, tail := splitPath(filename)
	h, err := t.findHandler(head)
	if err != nil {
		return nil, err
	}

	return h.WriteFile(c, tail)
}
