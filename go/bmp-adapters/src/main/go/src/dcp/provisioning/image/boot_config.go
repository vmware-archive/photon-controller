package image

import (
	"archive/tar"
	"bytes"
	"dcp/provisioning"
	"fmt"
	"html/template"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/golang/glog"
)

var genisoimagePath = "genisoimage"

var templateExt = ".tmpl"

type BootConfig struct {
	*provisioning.BootConfig

	Dir string

	generateProcess *os.Process
}

func NewBootConfig(config *provisioning.BootConfig) (*BootConfig, error) {
	dir, err := ioutil.TempDir("", "boot-config")
	if err != nil {
		return nil, err
	}

	c := BootConfig{
		BootConfig: config,

		Dir: dir,
	}

	return &c, nil
}

func (c *BootConfig) processFileEntry(entry provisioning.FileEntry) error {
	err := os.MkdirAll(filepath.Join(c.Dir, filepath.Dir(entry.Path)), 0755)
	if err != nil {
		return err
	}

	var r io.Reader

	// Create reader for contents of this file
	if entry.ContentsReference != "" {
		res, err := http.Get(entry.ContentsReference)
		if err == nil && res != nil && res.StatusCode != http.StatusOK {
			err = fmt.Errorf("%d (%s)", res.StatusCode, http.StatusText(res.StatusCode))
		}
		if err != nil {
			return fmt.Errorf("GET %s: %s", entry.ContentsReference, err)
		}

		r = res.Body
		defer res.Body.Close()
	} else {
		r = bytes.NewBufferString(entry.Contents)
	}

	// Interpret as template if needed
	if strings.HasSuffix(entry.Path, templateExt) {
		contents, err := ioutil.ReadAll(r)
		if err != nil {
			return err
		}

		t, err := template.New(entry.Path).Parse(string(contents))
		if err != nil {
			return err
		}

		b := &bytes.Buffer{}
		err = t.Execute(b, c.Data)
		if err != nil {
			return err
		}

		// Replace reader for contents with executed template
		r = b

		// Strip template extension from path
		entry.Path = strings.TrimSuffix(entry.Path, templateExt)
	}

	f, err := os.Create(filepath.Join(c.Dir, entry.Path))
	if err != nil {
		return err
	}

	defer f.Close()

	_, err = io.Copy(f, r)
	if err != nil {
		return err
	}

	return nil
}

func (c *BootConfig) Process() error {
	for _, entry := range c.Files {
		err := c.processFileEntry(entry)
		if err != nil {
			return err
		}
	}

	return nil
}

func (c *BootConfig) Cleanup() {
	// Kill process if one was started
	if c.generateProcess != nil {
		if c.generateProcess != nil {
			c.generateProcess.Kill()
		}
	}

	os.RemoveAll(c.Dir)
}

type commandPipe struct {
	io.Reader
	cmd *exec.Cmd
}

func (c *commandPipe) Read(b []byte) (int, error) {
	n, err := c.Reader.Read(b)

	// Reap process if read returned an error (including io.EOF)
	if err != nil {
		cmdError := c.cmd.Wait()
		if cmdError != nil {
			glog.Errorf("%s terminated with error: %s", c.cmd.Path, cmdError)
		} else {
			glog.Infof("%s terminated successfully", c.cmd.Path)
		}
	}

	return n, err
}

// ISO starts genisoimage and returns an io.Reader with its output.
func (c *BootConfig) ISO() (io.Reader, error) {
	cmd := exec.Command(genisoimagePath, "-R", "-V", c.Label, "-o", "-", c.Dir)
	pipe, err := cmd.StdoutPipe()
	if err != nil {
		return nil, err
	}

	err = cmd.Start()
	if err != nil {
		glog.Errorf("genisoimg could not be started: %s", err)
		return nil, err
	}

	glog.Infof("genisoimg started")

	c.generateProcess = cmd.Process

	return &commandPipe{
		Reader: pipe,
		cmd:    cmd,
	}, nil
}

// Tar returns an io.Reader for a generated tar file.
func (c *BootConfig) Tar() (io.Reader, error) {
	r, w := io.Pipe()
	t := tar.NewWriter(w)

	fn := func(path string, info os.FileInfo, err error) error {
		// Bubble up errors
		if err != nil {
			return err
		}

		// Skip directories
		if info.IsDir() {
			return nil
		}

		rel, err := filepath.Rel(c.Dir, path)
		if err != nil {
			return err
		}

		header := tar.Header{
			Name: rel,
			Size: info.Size(),
		}

		err = t.WriteHeader(&header)
		if err != nil {
			return err
		}

		f, err := os.Open(path)
		if err != nil {
			return err
		}

		_, err = io.Copy(t, f)
		return err
	}

	go func() {
		err := filepath.Walk(c.Dir, fn)
		if err != nil {
			w.CloseWithError(err)
		} else {
			w.Close()
		}
	}()

	return r, nil
}
