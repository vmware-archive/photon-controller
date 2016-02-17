package types

import (
	"encoding/json"
	"net"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIPMarshal(t *testing.T) {
	var i IP
	var b []byte

	i = IP{net.IPv4(1, 2, 3, 4)}
	b, err := json.Marshal(i)
	if err != nil {
		t.Fatal(err)
	}

	assert.Equal(t, `"1.2.3.4"`, string(b))
}

func TestIPUnmarshal(t *testing.T) {
	var i IP
	var b = []byte(`"1.2.3.4"`)

	err := json.Unmarshal(b, &i)
	if err != nil {
		t.Fatal(err)
	}

	assert.True(t, i.Equal(net.IPv4(1, 2, 3, 4)))
}
