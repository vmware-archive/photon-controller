package types

import (
	"encoding/json"
	"net"
)

type IPNet struct {
	*net.IPNet
}

func NewIPNet(cidr string) IPNet {
	_, net, err := net.ParseCIDR(cidr)
	if err != nil {
		panic(err)
	}

	// ParseCIDR should return non-nil "net" if err == nil
	if net == nil {
		panic("net cannot be nil")
	}

	return IPNet{net}
}

func (ip IPNet) MarshalJSON() ([]byte, error) {
	var s string

	if ip.IPNet != nil {
		s = ip.String()
	}

	return json.Marshal(s)
}

func (ip *IPNet) UnmarshalJSON(b []byte) error {
	var s string

	err := json.Unmarshal(b, &s)
	if err != nil {
		return err
	}

	_, net, err := net.ParseCIDR(s)
	if err != nil {
		return err
	}

	// ParseCIDR should return non-nil "net" if err == nil
	if net == nil {
		panic("net cannot be nil")
	}

	ip.IPNet = net
	return nil
}
