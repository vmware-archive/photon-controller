package gotftpd

import (
	"bytes"
	"errors"
	"io"
	"os"
	"testing"
	"testing/iotest"
	"time"

	"github.com/stretchr/testify/assert"
)

type rcBuffer struct {
	io.Reader
}

func (r *rcBuffer) Close() error {
	return nil
}

type wcBuffer struct {
	io.Writer
}

func (w *wcBuffer) Close() error {
	return nil
}

type handlerContext struct {
	snd chan interface{}
	rcv chan packet

	readFunc  func(c Conn, filename string) (ReadCloser, error)
	writeFunc func(c Conn, filename string) (WriteCloser, error)
}

func newHandlerContext() *handlerContext {
	h := &handlerContext{
		snd: make(chan interface{}, 1),
		rcv: make(chan packet, 1),
	}
	go func() {
		serve(nil, h, h, h)

		// No more packets can be sent by the server.
		close(h.rcv)
	}()
	return h
}

// To implement packetReader
func (h *handlerContext) read(timeout time.Duration) (packet, error) {
	select {
	case e, ok := <-h.snd:
		if !ok {
			return nil, ErrTimeout
		}

		switch t := e.(type) {
		case packet:
			return t, nil
		case error:
			return nil, t
		default:
			panic("")
		}
	}
}

// Implement packetWriter
func (h *handlerContext) write(p packet) error {
	h.rcv <- p
	return nil
}

// To implement Handler
func (h *handlerContext) ReadFile(c Conn, filename string) (ReadCloser, error) {
	if h.readFunc == nil {
		return &rcBuffer{&bytes.Buffer{}}, nil
	}
	return h.readFunc(c, filename)
}

// To implement Handler
func (h *handlerContext) WriteFile(c Conn, filename string) (WriteCloser, error) {
	if h.writeFunc == nil {
		return &wcBuffer{&bytes.Buffer{}}, nil
	}
	return h.writeFunc(c, filename)
}

func (h *handlerContext) SetReadCloser(r ReadCloser) {
	h.readFunc = func(_ Conn, _ string) (ReadCloser, error) {
		return r, nil
	}
}

func (h *handlerContext) SetWriteCloser(w WriteCloser) {
	h.writeFunc = func(_ Conn, _ string) (WriteCloser, error) {
		return w, nil
	}
}

func (h *handlerContext) Negotiate(t *testing.T, o map[string]string) {
	h.snd <- &packetRRQ{packetXRQ{options: o}}

	// Receive and validate OACK
	poack := <-h.rcv
	assert.IsType(t, &packetOACK{}, poack)
	oack := poack.(*packetOACK)

	// Validate that we got what we asked for
	for k, v := range o {
		assert.Equal(t, v, oack.options[k])
	}

	// Send ACK as response to OACK.
	h.snd <- &packetACK{blockNr: 0}
}

func TestMalformedFirstPacket(t *testing.T) {
	h := newHandlerContext()
	h.snd <- errOpcode

	px := <-h.rcv
	assert.IsType(t, &packetERROR{}, px)

	p := px.(*packetERROR)
	assert.Equal(t, p.errorCode, opcode(0))
	assert.Equal(t, p.errorMessage, "invalid opcode")
}

func TestUnexpectedFirstPacket(t *testing.T) {
	h := newHandlerContext()
	h.snd <- &packetACK{blockNr: uint16(1337)}

	px := <-h.rcv
	assert.IsType(t, &packetERROR{}, px)

	p := px.(*packetERROR)
	assert.Equal(t, p.errorCode, opcode(4))
}

func TestReadFileError(t *testing.T) {
	var tests = []struct {
		p            packet
		errorCode    uint16
		errorMessage string
	}{
		{
			&packetRRQ{packetXRQ{filename: "NotExists"}},
			1,
			os.ErrNotExist.Error(),
		},
		{
			&packetRRQ{packetXRQ{filename: "Permission"}},
			2,
			os.ErrPermission.Error(),
		},
		{
			&packetRRQ{packetXRQ{filename: "Default"}},
			0,
			"",
		},
	}

	for _, test := range tests {
		h := newHandlerContext()
		h.readFunc = func(_ Conn, filename string) (ReadCloser, error) {
			switch filename {
			case "NotExists":
				return nil, os.ErrNotExist
			case "Permission":
				return nil, os.ErrPermission
			default:
				return nil, errors.New("")
			}
		}

		h.snd <- test.p
		px := <-h.rcv
		assert.IsType(t, &packetERROR{}, px)

		p := px.(*packetERROR)
		assert.Equal(t, p.errorCode, test.errorCode)
		assert.Equal(t, p.errorMessage, test.errorMessage)
	}
}

func TestReadRequestNegotiation(t *testing.T) {
	var tests = []struct {
		opt      string
		proposed string
		returned string

		errorCode    uint16
		errorMessage string
	}{
		{
			opt:      "blksize",
			proposed: "", // Empty
			returned: "",

			errorCode:    8,
			errorMessage: "invalid syntax",
		},
		{
			opt:      "blksize",
			proposed: "xxx", // Not a number
			returned: "",

			errorCode:    8,
			errorMessage: "invalid syntax",
		},
		{
			opt:      "blksize",
			proposed: "7",
			returned: "8",
		},
		{
			opt:      "blksize",
			proposed: "65536",
			returned: "65464",
		},
		{
			opt:      "blksize",
			proposed: "12345",
			returned: "12345",
		},
		{
			opt:      "timeout",
			proposed: "", // Empty
			returned: "",

			errorCode:    8,
			errorMessage: "invalid syntax",
		},
		{
			opt:      "timeout",
			proposed: "xxx", // Not a number
			returned: "",

			errorCode:    8,
			errorMessage: "invalid syntax",
		},
		{
			opt:      "timeout",
			proposed: "0",
			returned: "1",
		},
		{
			opt:      "timeout",
			proposed: "256",
			returned: "255",
		},
		{
			opt:      "timeout",
			proposed: "32",
			returned: "32",
		},
	}

	for _, test := range tests {
		h := newHandlerContext()

		p := &packetRRQ{
			packetXRQ{
				options: map[string]string{
					test.opt: test.proposed,
				},
			},
		}

		h.snd <- p
		px := <-h.rcv

		switch p := px.(type) {
		case *packetERROR:
			assert.Equal(t, p.errorCode, test.errorCode)
			assert.Contains(t, p.errorMessage, test.errorMessage)
		case (*packetOACK):
			// Send ACK as response to OACK
			h.snd <- &packetACK{blockNr: 0}

			value, ok := p.options[test.opt]
			assert.True(t, ok)
			assert.Equal(t, value, test.returned)
		}
	}
}

func TestReadRequestChunks(t *testing.T) {
	var tests = []struct {
		buf     []byte
		packets []*packetDATA // DATA packets we expect to receive.
	}{
		{
			// Empty last packet.
			buf: []byte{0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf},
			packets: []*packetDATA{
				&packetDATA{blockNr: 1, data: []byte{0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7}},
				&packetDATA{blockNr: 2, data: []byte{0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf}},
				&packetDATA{blockNr: 3, data: []byte{}},
			},
		},
		{
			// Partial last packet.
			buf: []byte{0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe},
			packets: []*packetDATA{
				&packetDATA{blockNr: 1, data: []byte{0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7}},
				&packetDATA{blockNr: 2, data: []byte{0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe}},
			},
		},
	}

	for _, test := range tests {
		h := newHandlerContext()
		h.SetReadCloser(&rcBuffer{iotest.OneByteReader(bytes.NewBuffer(test.buf))})
		h.Negotiate(t, map[string]string{"blksize": "8"})

		for _, expected := range test.packets {
			pdata := <-h.rcv
			assert.IsType(t, &packetDATA{}, pdata)

			actual := pdata.(*packetDATA)
			assert.Equal(t, expected, actual)
			h.snd <- &packetACK{blockNr: actual.blockNr}
		}

		// There should not be any more packets.
		p, ok := <-h.rcv
		assert.False(t, ok)
		assert.Nil(t, p)
	}
}

func TestReadRequestRetries(t *testing.T) {
	h := newHandlerContext()

	buf := []byte{0x1}
	h.SetReadCloser(&rcBuffer{bytes.NewBuffer(buf)})
	h.Negotiate(t, map[string]string{"blksize": "8"})

	for i := 0; i < 2; i++ {
		// Throw away packet
		_ = <-h.rcv
		// Trigger timeout
		h.snd <- ErrTimeout
	}

	pdata := <-h.rcv
	assert.IsType(t, &packetDATA{}, pdata)

	data := pdata.(*packetDATA)
	assert.Equal(t, uint16(1), data.blockNr)
	assert.Equal(t, buf, data.data)
	h.snd <- &packetACK{blockNr: data.blockNr}
}
