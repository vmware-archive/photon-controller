package http

import (
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"slingshot/image/tftp"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestServer(t *testing.T) {
	var err error
	var addr *net.TCPAddr

	addr, err = net.ResolveTCPAddr("tcp4", "127.0.0.1:0")
	if err != nil {
		panic(err)
	}

	muxer := tftp.NewMuxer()
	s := NewServer(*addr, muxer)
	err = s.Run()
	if !assert.NoError(t, err) {
		return
	}

	// The server should now be listening for packets on some ephemeral port,
	// find out which one.
	addr = s.LocalAddr()
	if !assert.NotNil(t, addr) {
		return
	}

	pwd, err := os.Getwd()
	assert.NoError(t, err)

	muxer.Add("/bar", tftp.NewPathHandler(pwd))

	uri := url.URL{
		Scheme: "http",
		Host:   addr.String(),
	}

	tests := []struct {
		method string
		path   string
		expect int
	}{
		{"DELETE", "/foo", http.StatusMethodNotAllowed},
		{"GET", "/foo/server_test.go", http.StatusNotFound},
		{"HEAD", "/foo/server_test.go", http.StatusNotFound},
		{"GET", "/bar/server_test.go", http.StatusOK},
		{"HEAD", "/bar/server_test.go", http.StatusOK},
	}

	for _, test := range tests {
		uri.Path = test.path

		req, err := http.NewRequest(test.method, uri.String(), nil)
		assert.NoError(t, err)
		res, err := http.DefaultClient.Do(req)
		assert.NoError(t, err)
		assert.Equal(t, test.expect, res.StatusCode,
			fmt.Sprintf("%s %s", test.method, test.path))
	}

	s.Stop()
}
