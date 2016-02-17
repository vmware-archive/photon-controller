package dhcpv4

import "fmt"

type Validation interface {
	Validate(p Packet) error
}

func Validate(p Packet, vs []Validation) error {
	var err error

	for _, v := range vs {
		err = v.Validate(p)
		if err != nil {
			return err
		}
	}

	return nil
}

type validateMust struct {
	o    Option
	have bool
}

func (v validateMust) Validate(p Packet) error {
	var err error

	_, ok := p.GetOption(v.o)
	if v.have {
		// MUST HAVE
		if !ok {
			err = fmt.Errorf("dhcpv4: packet MUST have field %d", v.o)
		}
	} else {
		// MUST NOT HAVE
		if ok {
			err = fmt.Errorf("dhcpv4: packet MUST NOT have field %d", v.o)
		}
	}

	return err
}

func ValidateMustNot(o Option) Validation {
	return validateMust{o, false}
}

func ValidateMust(o Option) Validation {
	return validateMust{o, true}
}

type validateAllowedOptions struct {
	allowed map[Option]bool
}

func (v validateAllowedOptions) Validate(p Packet) error {
	var err error

	for k := range p.OptionMap {
		// If an option is not allowed, the packet MUST NOT have it.
		if !v.allowed[k] {
			err = fmt.Errorf("dhcpv4: packet MUST NOT have field %d", k)
			break
		}
	}

	return err
}

func ValidateAllowedOptions(os []Option) Validation {
	allowed := make(map[Option]bool)
	for _, o := range os {
		allowed[o] = true
	}

	return validateAllowedOptions{allowed}
}
