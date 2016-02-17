package gotftpd

import (
	"bytes"
	"encoding/binary"
	"io"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestFromWireErrors(t *testing.T) {
	var err error
	var b bytes.Buffer

	b.Reset()
	b.WriteByte(0x1)
	_, err = packetFromWire(&b)
	assert.Equal(t, err, io.ErrUnexpectedEOF)

	b.Reset()
	binary.Write(&b, binary.BigEndian, uint16(127)) // Invalid opcode
	_, err = packetFromWire(&b)
	assert.Equal(t, err, errOpcode)

	b.Reset()
	binary.Write(&b, binary.BigEndian, uint16(opcodeACK))
	binary.Write(&b, binary.BigEndian, uint16(0x1337))
	_, err = packetFromWire(&b)
	assert.Equal(t, err, nil)
}

func testReadPacketXRQ(t *testing.T, prefix []byte) {
	var b bytes.Buffer
	var p packet
	var err error

	{
		// No trailing NUL
		b.Reset()
		b.Write(prefix)
		b.WriteString("filename\x00")
		b.WriteString("netascii")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Too few entries
		b.Reset()
		b.Write(prefix)
		b.WriteString("filename\x00")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Invalid mode
		b.Reset()
		b.Write(prefix)
		b.WriteString("filename\x00")
		b.WriteString("invalid\x00")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, errMode)
	}

	{
		// No trailing NUL
		b.Reset()
		b.Write(prefix)
		b.WriteString("filename\x00")
		b.WriteString("netascii\x00")
		b.WriteString("opt1\x00")
		b.WriteString("value1\x00")
		b.WriteString("opt2")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Too few option entries
		b.Reset()
		b.Write(prefix)
		b.WriteString("filename\x00")
		b.WriteString("netascii\x00")
		b.WriteString("opt1\x00")
		b.WriteString("value1\x00")
		b.WriteString("opt2\x00")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Well formed packet
		b.Reset()
		b.Write(prefix)
		b.WriteString("filename\x00")
		b.WriteString("netascii\x00")
		b.WriteString("opt1\x00")
		b.WriteString("value1\x00")
		b.WriteString("OPT2\x00")
		b.WriteString("VALUE2\x00")
		p, err = packetFromWire(&b)
		assert.Nil(t, err)

		var value string
		var ok bool

		var px *packetXRQ
		switch py := p.(type) {
		case (*packetRRQ):
			px = &py.packetXRQ
		case (*packetWRQ):
			px = &py.packetXRQ
		}

		assert.Equal(t, px.filename, "filename")
		assert.Equal(t, px.mode, modeNETASCII)

		value, ok = px.options["opt1"]
		assert.Equal(t, value, "value1")
		assert.True(t, ok)

		value, ok = px.options["opt2"]
		assert.Equal(t, value, "value2")
		assert.True(t, ok)
	}
}

func TestReadPacketRRQ(t *testing.T) {
	testReadPacketXRQ(t, []byte{0x0, uint8(opcodeRRQ)})
}

func TestWritePacketRRQ(t *testing.T) {
	var b = bytes.Buffer{}
	var err error

	p := &packetRRQ{}
	p.filename = "filename"
	p.mode = modeNETASCII
	p.options = make(map[string]string)
	p.options["opt"] = "value"

	err = packetToWire(p, &b)
	assert.Nil(t, err)
	assert.Equal(t, []byte("\x00\x01filename\x00netascii\x00opt\x00value\x00"), b.Bytes())
}

func TestReadPacketWRQ(t *testing.T) {
	testReadPacketXRQ(t, []byte{0x0, uint8(opcodeWRQ)})
}

func TestWritePacketWRQ(t *testing.T) {
	var b = bytes.Buffer{}
	var err error

	p := &packetWRQ{}
	p.filename = "filename"
	p.mode = modeNETASCII
	p.options = make(map[string]string)
	p.options["opt"] = "value"

	err = packetToWire(p, &b)
	assert.Nil(t, err)
	assert.Equal(t, []byte("\x00\x02filename\x00netascii\x00opt\x00value\x00"), b.Bytes())
}

func TestReadPacketDATA(t *testing.T) {
	var b *bytes.Buffer
	var p packet
	var err error

	{
		// Too short
		b = &bytes.Buffer{}
		b.Write([]byte{0x0, uint8(opcodeDATA)})
		b.Write([]byte{0x7f})
		_, err = packetFromWire(b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Well formed
		b = &bytes.Buffer{}
		b.Write([]byte{0x0, uint8(opcodeDATA)})
		b.Write([]byte{0x0, 0x7f})
		b.WriteString("hello world\n")
		p, err = packetFromWire(b)
		assert.Nil(t, err)

		px := p.(*packetDATA)
		assert.Equal(t, px.blockNr, uint16(0x7f))
		assert.Equal(t, px.data, []byte("hello world\n"))
	}
}

func TestWritePacketDATA(t *testing.T) {
	var b bytes.Buffer
	var err error

	p := &packetDATA{}
	p.blockNr = 0x1337
	p.data = []byte{0x12, 0x13}

	err = packetToWire(p, &b)
	assert.Nil(t, err)
	assert.Equal(t, []byte("\x00\x03\x13\x37\x12\x13"), b.Bytes())
}

func TestPacketACK(t *testing.T) {
	var b bytes.Buffer
	var p packet
	var err error

	{
		// Too short
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeACK)})
		b.Write([]byte{0x7f})
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Well formed
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeACK)})
		b.Write([]byte{0x0, 0x7f})
		p, err = packetFromWire(&b)
		assert.Nil(t, err)

		px := p.(*packetACK)
		assert.Equal(t, px.blockNr, uint16(0x7f))
	}
}

func TestWritePacketACK(t *testing.T) {
	var b bytes.Buffer
	var err error

	p := &packetACK{}
	p.blockNr = 0x1337

	err = packetToWire(p, &b)
	assert.Nil(t, err)
	assert.Equal(t, []byte("\x00\x04\x13\x37"), b.Bytes())
}

func TestPacketERROR(t *testing.T) {
	var b bytes.Buffer
	var p packet
	var err error

	{
		// Too short
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeERROR)})
		b.Write([]byte{0x7f})
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// No trailing space after error message
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeERROR)})
		b.Write([]byte{0x0, 0x01})
		b.WriteString("error")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Well formed without error string
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeERROR)})
		b.Write([]byte{0x0, 0x01})
		b.WriteString("\x00")
		p, err = packetFromWire(&b)
		assert.Nil(t, err)

		px := p.(*packetERROR)
		assert.Equal(t, px.errorCode, uint16(0x01))
		assert.Equal(t, px.errorMessage, "")
	}

	{
		// Well formed with error string
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeERROR)})
		b.Write([]byte{0x0, 0x01})
		b.WriteString("error\x00")
		p, err = packetFromWire(&b)
		assert.Nil(t, err)

		px := p.(*packetERROR)
		assert.Equal(t, px.errorCode, uint16(0x01))
		assert.Equal(t, px.errorMessage, "error")
	}
}

func TestWritePacketERROR(t *testing.T) {
	var b bytes.Buffer
	var err error

	p := &packetERROR{}
	p.errorCode = 0x1
	p.errorMessage = "error!"

	err = packetToWire(p, &b)
	assert.Nil(t, err)
	assert.Equal(t, []byte("\x00\x05\x00\x01error!\x00"), b.Bytes())
}

func TestPacketOPACK(t *testing.T) {
	var b bytes.Buffer
	var p packet
	var err error

	{
		// No trailing NUL
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeOACK)})
		b.WriteString("opt1\x00")
		b.WriteString("value1\x00")
		b.WriteString("opt2")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Too few option entries
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeOACK)})
		b.WriteString("opt1\x00")
		b.WriteString("value1\x00")
		b.WriteString("opt2\x00")
		_, err = packetFromWire(&b)
		assert.Equal(t, err, io.ErrUnexpectedEOF)
	}

	{
		// Well formed packet
		b.Reset()
		b.Write([]byte{0x0, uint8(opcodeOACK)})
		b.WriteString("opt1\x00")
		b.WriteString("value1\x00")
		b.WriteString("OPT2\x00")
		b.WriteString("VALUE2\x00")
		p, err = packetFromWire(&b)
		assert.Nil(t, err)

		var value string
		var ok bool

		px := p.(*packetOACK)

		value, ok = px.options["opt1"]
		assert.Equal(t, value, "value1")
		assert.True(t, ok)

		value, ok = px.options["opt2"]
		assert.Equal(t, value, "value2")
		assert.True(t, ok)
	}
}

func TestWritePacketOPACK(t *testing.T) {
	var b bytes.Buffer
	var err error

	p := &packetOACK{}
	p.options = make(map[string]string)
	p.options["opt"] = "value"

	err = packetToWire(p, &b)
	assert.Nil(t, err)
	assert.Equal(t, []byte("\x00\x06opt\x00value\x00"), b.Bytes())
}
