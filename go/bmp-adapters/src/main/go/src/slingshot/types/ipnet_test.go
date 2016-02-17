package types

import (
	"encoding/json"
	"net"
	"testing"

	"github.com/stretchr/testify/assert"
)

func parseCIDR(s string) *net.IPNet {
	_, net, err := net.ParseCIDR(s)
	if err != nil {
		panic(err)
	}

	return net
}

func TestIPNetMarshal(t *testing.T) {
	var i IPNet
	var b []byte

	b, err := json.Marshal(i)
	if err != nil {
		t.Fatal(err)
	}

	i = IPNet{parseCIDR("1.2.3.4/24")}
	b, err = json.Marshal(i)
	if err != nil {
		t.Fatal(err)
	}

	assert.Equal(t, `"1.2.3.0/24"`, string(b))
}

func TestIPNetUnmarshal(t *testing.T) {
	var i IPNet
	var b = []byte(`"1.2.3.4/24"`)

	err := json.Unmarshal(b, &i)
	if err != nil {
		t.Fatal(err)
	}

	ones, bits := i.Mask.Size()
	assert.Equal(t, 24, ones)
	assert.Equal(t, 32, bits)

	assert.True(t, i.IP.Equal(net.IPv4(1, 2, 3, 0)))
}
