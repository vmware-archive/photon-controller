package types

import (
	"encoding/json"
	"fmt"
	"net"
)

type IP struct {
	net.IP
}

func NewIP(s string) IP {
	ip := net.ParseIP(s)
	if ip == nil {
		panic("invalid ip")
	}

	return IP{ip}
}

func (ip IP) MarshalJSON() ([]byte, error) {
	if ip.IP == nil {
		return json.Marshal(nil)
	}
	return json.Marshal(ip.String())
}

func (ip *IP) UnmarshalJSON(b []byte) error {
	var s string

	err := json.Unmarshal(b, &s)
	if err != nil {
		return err
	}

	ip.IP = net.ParseIP(s)
	if ip.IP == nil {
		return fmt.Errorf("types: invalid IP")
	}

	return nil
}
