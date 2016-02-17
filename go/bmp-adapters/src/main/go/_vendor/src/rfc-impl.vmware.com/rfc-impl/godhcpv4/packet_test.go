package dhcpv4

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

type testPacket struct {
	buf []byte

	offsetOption int
	offsetFile   int
	offsetSName  int
}

func (t *testPacket) writeOptionAtOffset(offset int, o Option, v []byte) int {
	var lv int

	p := t.buf
	switch o {
	case OptionPad, OptionEnd:
		lv = 1
	default:
		lv = 2 + len(v)
	}

	if offset+lv > cap(p) {
		p = make([]byte, offset+lv)
		copy(p, t.buf)
		t.buf = p
	}

	// Write option to offset
	q := p[offset : offset+lv]
	q[0] = byte(o)
	if lv > 1 {
		q[1] = byte(len(v))
		copy(q[2:], v)
	}

	return len(q)
}

func (t *testPacket) appendToOption(o Option, v []byte) int {
	if t.offsetOption == 0 {
		t.offsetOption = 240
	}

	n := t.writeOptionAtOffset(t.offsetOption, o, v)
	t.offsetOption += n
	return n
}

func (t *testPacket) appendToFile(o Option, v []byte) int {
	if t.offsetFile == 0 {
		t.offsetFile = 108
	}

	n := t.writeOptionAtOffset(t.offsetFile, o, v)
	t.offsetFile += n
	if t.offsetFile > 236 {
		panic("overflow in file field")
	}

	return n
}

func (t *testPacket) appendToSName(o Option, v []byte) int {
	if t.offsetSName == 0 {
		t.offsetSName = 44
	}

	n := t.writeOptionAtOffset(t.offsetSName, o, v)
	t.offsetSName += n
	if t.offsetSName > 108 {
		panic("overflow in sname field")
	}

	return n
}

func fromBytes(t *testing.T, b []byte) (Packet, error) {
	var p Packet
	var err error

	for i := 240; i < len(b); i++ {
		p, err = PacketFromBytes(b[0 : i+1])
		if i+1 == len(b) {
			assert.Nil(t, err, "expected no error with i=%d", i)
		} else {
			assert.Equal(t, ErrShortPacket, err, "expect short packet error with i=%d", i)
		}
	}

	return p, err
}

func assertOption(t *testing.T, opts OptionMap, o Option, expected []byte) {
	actual, ok := opts[o]
	assert.True(t, ok)
	assert.Equal(t, expected, actual)
}

func TestPacketFromBytes(t *testing.T) {
	var p *testPacket

	// Fabricate packet
	p = new(testPacket)
	p.appendToOption(OptionSubnetMask, []byte{0x12})
	p.appendToOption(OptionEnd, nil)
	if pckt, err := fromBytes(t, p.buf); assert.Nil(t, err) {
		o := pckt.OptionMap
		assert.Equal(t, 1, len(o))
		assertOption(t, o, OptionSubnetMask, []byte{0x12})
	}

	// Fabricate packet with overload into `file` field
	p = new(testPacket)
	p.appendToOption(OptionSubnetMask, []byte{0x12})
	p.appendToOption(OptionOverload, []byte{0x1})
	p.appendToOption(OptionEnd, nil)
	p.appendToFile(OptionTimeOffset, []byte{0x34})
	p.appendToFile(OptionEnd, nil)
	if pckt, err := fromBytes(t, p.buf); assert.Nil(t, err) {
		o := pckt.OptionMap
		assert.Equal(t, 3, len(o))
		assertOption(t, o, OptionSubnetMask, []byte{0x12})
		assertOption(t, o, OptionOverload, []byte{0x1})
		assertOption(t, o, OptionTimeOffset, []byte{0x34})
	}

	// Fabricate packet with overload into `sname` field
	p = new(testPacket)
	p.appendToOption(OptionSubnetMask, []byte{0x12})
	p.appendToOption(OptionOverload, []byte{0x2})
	p.appendToOption(OptionEnd, nil)
	p.appendToSName(OptionRouter, []byte{0x56})
	p.appendToSName(OptionEnd, nil)
	if pckt, err := fromBytes(t, p.buf); assert.Nil(t, err) {
		o := pckt.OptionMap
		assert.Equal(t, 3, len(o))
		assertOption(t, o, OptionSubnetMask, []byte{0x12})
		assertOption(t, o, OptionOverload, []byte{0x2})
		assertOption(t, o, OptionRouter, []byte{0x56})
	}

	// Fabricate packet with overload into `file` AND `sname` fields
	p = new(testPacket)
	p.appendToOption(OptionSubnetMask, []byte{0x12})
	p.appendToOption(OptionOverload, []byte{0x3})
	p.appendToOption(OptionEnd, nil)
	p.appendToFile(OptionTimeOffset, []byte{0x34})
	p.appendToFile(OptionEnd, nil)
	p.appendToSName(OptionRouter, []byte{0x56})
	p.appendToSName(OptionEnd, nil)
	if pckt, err := fromBytes(t, p.buf); assert.Nil(t, err) {
		o := pckt.OptionMap
		assert.Equal(t, 4, len(o))
		assertOption(t, o, OptionSubnetMask, []byte{0x12})
		assertOption(t, o, OptionOverload, []byte{0x3})
		assertOption(t, o, OptionTimeOffset, []byte{0x34})
		assertOption(t, o, OptionRouter, []byte{0x56})
	}
}

func assertEqualOptionMaps(t *testing.T, o1, o2 OptionMap) bool {
	compare := func(o1, o2 OptionMap) bool {
		for k, v1 := range o1 {
			// Skip
			if k == OptionOverload {
				continue
			}

			v2, ok := o2[k]
			if !assert.True(t, ok, "expected key %s to be present in both option maps", k) {
				return false
			}
			if !assert.Equal(t, v1, v2, "expected values for key %s to be equal", k) {
				return false
			}
		}
		return true
	}
	return compare(o1, o2) && compare(o2, o1)
}

func TestPacketToBytes(t *testing.T) {
	option := 1
	p := NewPacket(BootRequest)

	createOptionMaps := func() (OptionMap, OptionMap, bool) {
		b, err := PacketToBytes(p, nil)
		if !assert.Nil(t, err) {
			return nil, nil, false
		}

		q, err := PacketFromBytes(b)
		if !assert.Nil(t, err) {
			return nil, nil, false
		}

		return p.OptionMap, q.OptionMap, true
	}

	fillOptions := func(upto int) bool {
		for l := upto; l >= 3; option++ {
			s := l

			// As values may not be > 255 bytes, the total number of bytes required
			// for an option may not be > 257 (the two extra bytes are for the option
			// tag and the value length).
			if s > 257 {
				s = 257
			}

			p.SetOption(Option(option), make([]byte, s-2))
			l -= s

			o1, o2, ok := createOptionMaps()
			if !ok || !assertEqualOptionMaps(t, o1, o2) {
				return false
			}
		}

		return true
	}

	// Fill up options field
	{
		// Maximum packet length - base packet length - OptionOverload - OptionEnd
		omax := 1500 - 240 - 3 - 1
		if !fillOptions(omax) {
			return
		}
	}

	// Fill up File field
	{
		// File field end - File field offset - OptionEnd
		fmax := 236 - 108 - 1
		if !fillOptions(fmax) {
			return
		}
	}

	// Fill up SName field
	{
		// SName field end - SName field offset - OptionEnd
		smax := 108 - 44 - 1
		if !fillOptions(smax) {
			return
		}
	}
}
