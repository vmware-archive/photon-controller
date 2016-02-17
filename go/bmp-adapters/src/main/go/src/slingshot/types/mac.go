package types

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net"
	"strings"
)

type MAC struct {
	net.HardwareAddr
}

func NewMAC(s string) MAC {
	hw, err := net.ParseMAC(s)
	if err != nil {
		panic(err)
	}

	return MAC{hw}
}

func NewMACFromBytes(b []byte) (*MAC, error) {
	s := ""
	for _, e := range b {
		if s != "" {
			s += ":"
		}
		s += fmt.Sprintf("%02x", e)
	}

	hw, err := net.ParseMAC(s)
	if err != nil {
		return nil, err
	}

	return &MAC{hw}, nil
}

func (m MAC) String() string {
	return m.HardwareAddr.String()
}

func (m MAC) Equal(n MAC) bool {
	return bytes.Equal(m.HardwareAddr, n.HardwareAddr)
}

func (m MAC) MarshalJSON() ([]byte, error) {
	return json.Marshal(m.HardwareAddr.String())
}

func (m *MAC) UnmarshalJSON(b []byte) error {
	var s string

	err := json.Unmarshal(b, &s)
	if err != nil {
		return err
	}

	// Canonicalize if not in canonical format
	if !strings.ContainsAny(s, ":-") {
		// Add : between every pair of characters
		var sout = ""
		for i := 0; i < len(s); i += 2 {
			if i+2 < len(s) {
				sout += s[i : i+2]
				sout += ":"
			} else {
				sout += s[i:]
			}
		}

		s = sout
	}

	m.HardwareAddr, err = net.ParseMAC(s)
	if err != nil {
		return err
	}

	return nil
}
