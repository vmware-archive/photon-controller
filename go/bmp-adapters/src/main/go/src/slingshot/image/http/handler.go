package http

import (
	"io"
	"net"
	"net/http"
	"os"
	"time"

	"rfc-impl.vmware.com/rfc-impl/gotftpd"
	"github.com/golang/glog"
)

type Handler struct {
	gotftpd.Handler

	local  *net.TCPAddr
	remote *net.TCPAddr
}

func (h Handler) LocalAddr() net.Addr {
	return h.local
}

func (h Handler) RemoteAddr() net.Addr {
	return h.remote
}

func (h Handler) get(w http.ResponseWriter, req *http.Request) error {
	rc, err := h.ReadFile(h, req.URL.Path)
	if err != nil {
		return err
	}

	defer rc.Close()

	// Set Content-Type to octet-stream. We don't care what it is, nor do we want
	// the http package to sniff and figure it out by itself.
	w.Header().Set("Content-Type", "application/octet-stream")

	if rs, ok := rc.(io.ReadSeeker); ok {
		// http.ServeContent handles Content-Length, range requests, etc.
		http.ServeContent(w, req, "", time.Time{}, rs)
	} else {
		_, err = io.Copy(w, rc)
		if err != nil {
			glog.Info(err)
		}
	}

	return nil
}

func (h Handler) ServeHTTP(w http.ResponseWriter, req *http.Request) {
	switch req.Method {
	case "GET", "HEAD":
		if err := h.get(w, req); err != nil {
			glog.Infof("Serve HTTP get local %s failed %s", req.URL.Path, err)
			h.fail(w, err)
		}
	default:
		h.status(w, http.StatusMethodNotAllowed)
	}
}

func (h Handler) fail(w http.ResponseWriter, err error) {
	switch err {
	case os.ErrNotExist:
		h.status(w, http.StatusNotFound)
	default:
		h.status(w, http.StatusInternalServerError)
		glog.Infof("Error: %v (%#v)", err, err)
	}
}

func (Handler) status(w http.ResponseWriter, code int) {
	http.Error(w, http.StatusText(code), code)
}
