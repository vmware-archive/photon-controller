package types

import (
	"encoding/json"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestMACMarshal(t *testing.T) {
	b, err := json.Marshal(NewMAC("01:23:45:67:89:ab"))
	if err != nil {
		t.Fatal(err)
	}

	assert.Equal(t, `"01:23:45:67:89:ab"`, string(b))
}

func TestMACUnmarshal(t *testing.T) {
	var m MAC
	var b = []byte(`"01:23:45:67:89:ab"`)

	err := json.Unmarshal(b, &m)
	if err != nil {
		t.Fatal(err)
	}

	assert.Equal(t, "01:23:45:67:89:ab", m.String())
}

func TestMACUnmarshalCanonical(t *testing.T) {
	var m MAC
	var b = []byte(`"0123456789Ab"`)

	err := json.Unmarshal(b, &m)
	if err != nil {
		t.Fatal(err)
	}

	assert.Equal(t, "01:23:45:67:89:ab", m.String())
}

func TestNewMACFromBytes(t *testing.T) {
	var b = []byte{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}
	var mac *MAC
	var err error

	_, err = NewMACFromBytes(b[0:7])
	assert.Error(t, err)

	mac, err = NewMACFromBytes(b[0:6])
	assert.NoError(t, err)
	assert.Equal(t, mac.String(), "01:02:03:04:05:06")
}
