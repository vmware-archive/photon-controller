package main

import (
	"rfc-impl.vmware.com/rfc-impl/gotftpd"
	"log"
	"os"
	"path"
)

type Handler struct {
	Path string
}

func (h Handler) ReadFile(c gotftpd.Conn, filename string) (gotftpd.ReadCloser, error) {
	log.Printf("Request from %s to read %s", c.RemoteAddr(), filename)
	return os.OpenFile(path.Join(h.Path, filename), os.O_RDONLY, 0)
}

func (h Handler) WriteFile(c gotftpd.Conn, filename string) (gotftpd.WriteCloser, error) {
	log.Printf("Request from %s to write %s", c.RemoteAddr(), filename)
	return os.OpenFile(path.Join(h.Path, filename), os.O_WRONLY, 0644)
}

func main() {
	pwd, err := os.Getwd()
	if err != nil {
		panic(err)
	}

	h := Handler{Path: pwd}
	err = gotftpd.ListenAndServe(h)
	panic(err)
}
