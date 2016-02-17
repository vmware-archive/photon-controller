package dhcpv4

import (
	"bytes"
	"encoding/binary"
	"net"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func TestOptionMapOption(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b []byte

	om := make(OptionMap)

	_, ok = om.GetOption(o)
	assert.False(t, ok)

	a = []byte("foo")
	om.SetOption(o, a)

	b, ok = om.GetOption(o)
	assert.True(t, ok)
	assert.Equal(t, a, b)
}

func TestOptionMapMessageType(t *testing.T) {
	var a, b MessageType

	om := make(OptionMap)

	b = om.GetMessageType()
	assert.Equal(t, MessageType(0), b)

	a = MessageType(1)
	om.SetMessageType(a)

	b = om.GetMessageType()
	assert.Equal(t, a, b)
}

func TestOptionMapUint8(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b uint8

	om := make(OptionMap)

	_, ok = om.GetUint8(o)
	assert.False(t, ok)

	a = uint8(37)
	om.SetUint8(o, a)

	b, ok = om.GetUint8(o)
	assert.True(t, ok)
	assert.Equal(t, a, b)
}

func TestOptionMapUint16(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b uint16

	om := make(OptionMap)

	_, ok = om.GetUint16(o)
	assert.False(t, ok)

	a = uint16(37000)
	om.SetUint16(o, a)

	b, ok = om.GetUint16(o)
	assert.True(t, ok)
	assert.Equal(t, a, b)
}

func TestOptionMapUint32(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b uint32

	om := make(OptionMap)

	_, ok = om.GetUint32(o)
	assert.False(t, ok)

	a = uint32(37000000)
	om.SetUint32(o, a)

	b, ok = om.GetUint32(o)
	assert.True(t, ok)
	assert.Equal(t, a, b)
}

func TestOptionMapString(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b string

	om := make(OptionMap)

	_, ok = om.GetString(o)
	assert.False(t, ok)

	a = "hello world!"
	om.SetString(o, a)

	b, ok = om.GetString(o)
	assert.True(t, ok)
	assert.Equal(t, a, b)
}

func TestOptionMapIP(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b net.IP

	om := make(OptionMap)

	_, ok = om.GetIP(o)
	assert.False(t, ok)

	a = net.IPv4(1, 2, 3, 4)
	om.SetIP(o, a)

	b, ok = om.GetIP(o)
	assert.True(t, ok)
	assert.Equal(t, a, b)
}

func TestOptionMapDuration(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b time.Duration

	om := make(OptionMap)

	_, ok = om.GetDuration(o)
	assert.False(t, ok)

	a = 100 * time.Second
	om.SetDuration(o, a)

	b, ok = om.GetDuration(o)
	assert.True(t, ok)
	assert.Equal(t, a, b)
}

func TestOptionMapDurationTruncateSubSecond(t *testing.T) {
	var o = Option(1)
	var ok bool
	var a, b time.Duration

	om := make(OptionMap)

	a = 100*time.Second + 100*time.Millisecond
	om.SetDuration(o, a)

	b, ok = om.GetDuration(o)
	assert.True(t, ok)
	assert.Equal(t, 100*time.Second, b)
}

// Keep this function here until we have a generic option getter/setter for any
// type that the option map supports.
func encodeInteger(src interface{}) []byte {
	var b bytes.Buffer
	binary.Write(&b, binary.BigEndian, src)
	return b.Bytes()
}

func TestOptionMapDecodeEncodeWithoutPtr(t *testing.T) {
	om := make(OptionMap)

	var s struct {
		U8  uint8  `code:"10"`
		U16 uint16 `code:"11"`
		U32 uint32 `code:"12"`
		I8  int8   `code:"20"`
		I16 int16  `code:"21"`
		I32 int32  `code:"22"`
		S   string `code:"30"`
	}

	om.SetOption(Option(10), encodeInteger(uint8(32)))
	om.SetOption(Option(11), encodeInteger(uint16(32000)))
	om.SetOption(Option(12), encodeInteger(uint32(32000000)))
	om.SetOption(Option(20), encodeInteger(int8(-32)))
	om.SetOption(Option(21), encodeInteger(int16(-32000)))
	om.SetOption(Option(22), encodeInteger(int32(-32000000)))
	om.SetString(Option(30), "thirtytwo")

	om.Decode(&s)

	assert.Equal(t, uint8(32), s.U8)
	assert.Equal(t, uint16(32000), s.U16)
	assert.Equal(t, uint32(32000000), s.U32)
	assert.Equal(t, int8(-32), s.I8)
	assert.Equal(t, int16(-32000), s.I16)
	assert.Equal(t, int32(-32000000), s.I32)
	assert.Equal(t, "thirtytwo", s.S)

	omX := make(OptionMap)
	omX.Encode(&s)
	assert.Equal(t, om, omX)
}

func TestOptionMapDecodeEncodeWithPtr(t *testing.T) {
	om := make(OptionMap)

	var s struct {
		U8  *uint8  `code:"10"`
		U16 *uint16 `code:"11"`
		U32 *uint32 `code:"12"`
		I8  *int8   `code:"20"`
		I16 *int16  `code:"21"`
		I32 *int32  `code:"22"`
		S   *string `code:"30"`
	}

	om.SetOption(Option(10), encodeInteger(uint8(32)))
	om.SetOption(Option(11), encodeInteger(uint16(32000)))
	om.SetOption(Option(12), encodeInteger(uint32(32000000)))
	om.SetOption(Option(20), encodeInteger(int8(-32)))
	om.SetOption(Option(21), encodeInteger(int16(-32000)))
	om.SetOption(Option(22), encodeInteger(int32(-32000000)))
	om.SetString(Option(30), "thirtytwo")

	om.Decode(&s)

	if assert.NotNil(t, s.U8) {
		assert.Equal(t, uint8(32), *s.U8)
	}
	if assert.NotNil(t, s.U16) {
		assert.Equal(t, uint16(32000), *s.U16)
	}
	if assert.NotNil(t, s.U32) {
		assert.Equal(t, uint32(32000000), *s.U32)
	}
	if assert.NotNil(t, s.I8) {
		assert.Equal(t, int8(-32), *s.I8)
	}
	if assert.NotNil(t, s.I16) {
		assert.Equal(t, int16(-32000), *s.I16)
	}
	if assert.NotNil(t, s.I32) {
		assert.Equal(t, int32(-32000000), *s.I32)
	}
	if assert.NotNil(t, s.S) {
		assert.Equal(t, "thirtytwo", *s.S)
	}

	omX := make(OptionMap)
	omX.Encode(&s)
	assert.Equal(t, om, omX)
}
