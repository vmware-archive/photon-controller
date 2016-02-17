package http

import (
	"errors"
	"net"
)

// fakeListener implements net.Listener.
// It returns its net.Conn upon the first call to Accept().
// Subsequent invocations of Accept() return an error.
type fakeListener struct {
	conn net.Conn
}

var errFakeListenerDone = errors.New("fake listener is done")

func (f *fakeListener) Accept() (net.Conn, error) {
	if f.conn == nil {
		return nil, errFakeListenerDone
	}

	conn := f.conn
	f.conn = nil
	return conn, nil
}

func (f *fakeListener) Close() error {
	return nil
}

func (f *fakeListener) Addr() net.Addr {
	if f.conn == nil {
		return nil
	}

	return f.conn.LocalAddr()
}
