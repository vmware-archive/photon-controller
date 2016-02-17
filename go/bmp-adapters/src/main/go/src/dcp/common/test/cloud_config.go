package test

import "dcp/provisioning"

var cloudConfigTemplateSource = `#cloud-config

ssh_authorized_keys:
  - ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDVRBoO+nS4GDL8m8Qv4s8tjSDbVjBV+wZgRyoTDpzGSN34MBct0AfYMxfwUdq7u+IEzqTJw01t0jxwN6YEPDIt0Et1z54+DoTnjLISnaDzd6btmsW7dMFSEYLysOgeOkE+WC3pTl41JZ7UjEHAq7VWwpsy//UjjIbD3incZq1V/qWhCNPOleZ1Ikc9IiaHADC2jqHlypQ0Wk9JN8ktCVT2Pgw+t1j4MR8PIpndXFYFYcgV5T/g8Mskm3DR18JwFDSmDTDblFeKT76cN5hCn5XEdy/cP/UbOhLwY27hUkyTwjmgvNnRB3WGMUFUW9ov/CoyVOhXvYO/sSafao5UQuxL jenkins@host
  - ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAIEA3bD+NQas1AsBQKowKTnLiPK49mNLv4MYP4cWLVAwq0GczTIN0rtVSiidnOns2/KNbWagmv61mSqpB8VuGmj2XrQneo6LVBk4yM130eB3/ZJSxOS+GjV9kIaoQGNaIHClHobQNSfxv6DCWCVxK5KYKwF+uGwC5tgPmuA6qzawpDc= dougm@hawk
  - ssh-dss AAAAB3NzaC1kc3MAAACBAJVadtNKxmv2esXLQjC+QtCH31uhYdJqTofUuOaHceFpgoRL5IweZWHszzUcpI+JWUqozJU5nWmnXA3y7Eu86RLqtohdJCzJLvhoKr9s74V/0XptkJPW/S+Sq+Uj7Fehzv4uzrNJywims1IHJgrDScVPMKmxTkoI+EbrLoSyT8XzAAAAFQCFkDh8DbaSBTFM5LTFVzp7gf+uDwAAAIB8FfrIFYf70wo3NKAvdoj/OvL67j/8S3klCFn4w5MNJi7PxAL7MwCYitfW+6uSk+dMACrU95VxxpcPV1hP6V6OOM0a6CAsbcoDOsA3VPNItGxYE0Zbhz6vltHVU6ZCFpdNHlLxzBkliLYcd0ET5Fxww/PR/em4n3FQQgmwfZ1DkQAAAIBXbyGh7VXGJMjw6QN3Y0eor6ZQL14Nav08biJrAKjNqTgiMP5qn+BwH/wNOUDZQODX7xkD1+fhiRU+h+XBBT4tAyl4ImPSlukyjY5uR5yXI8BHQjHU9x2CYZhwXGAQnzAP4lzZbQ/QaJefe9h4pQIfnstQ1NqsIzH+hsqeywk3vw== faiyaza@houston
  - ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC6M1LMYLqsbZZJVQBp84vP2GVHfNUkNJ6fsKX3fNVMox0nh5GXYA9zarMJQuYiak9Bn5b/Inn7dFkUnsOC9EMdx6yuiiWC4k4//GefeKIe7cTuMQcSOiMJJvUJxtfWPoKOt8l+94YgYu98Gx2vymXkB9eUuyIw6i+DdsM5Tywi6sc8hOdB/SMh4+uzdYZZxVfQR4BrAgPoRd2e9RoywqDmNh4p/M/T+mQ5i+NwEM4czk9DkphJTbraRHMbnEg1kEkmUI4hQHbU0hHDS+huVYr9FQOgwKFMX5y55QsAqphM10ARYud9FA4Dh8pIcgo4X0PC3mFrqj3TejwsEtOSEPnD georgioschrysanthakopoulos@Georgioss-MacBook-Pro.local
  - ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQD0tmJRVtHWszC6HzNjwWnxr4HPRDiBqBs75KuAZzdyAhnPL5No5/+0CS5rn+57W+zsG5d9Hq9z/DGvc73Jou+5Uh5L6Hvgk7fJugLrs+aKGd87X3/urrK4AolEwWD9wVVSKHOX2pGRWEAZFAVHC59FJBn0uVIAy+SZQ01WLMjgeZcWtcH9CIeSyNLU7hMAZ+2uEEDySw03I54bP7iRHzSR/g3NMii4XVFcRZ9WQU7dxqEKro5LlZY4Pk89FeHNWAEH9Iew10T0OqmLgiPfw5yLNJRgmJqe166YELQjiz5WoEYyiUgXjoDszfFabEOonKtS5ms+8CVrcbIdhDheKToT georgechrysanthakopoulos@github.com
  - ssh-rsa AAAAB3NzaC1yc2EAAAABIwAAAQEAukFORGhYHStw7D4vCxwFP93soYg6Bgl9/DfjXls6H4W6MoKTAJcHgpZuvZCb40A06q5AdkINGppDbNPJgDy+gbF8wQAg/qvOc2QIZLrWDL7Cqa8RimoKBou6/336cvec98emdbfS1vZTPk/ptyujG9dxbiby2mF3+2iWtFKonlGGNdU8uhFOLMGiCzSRpPM2kPkXy218W4UVe5SprIZiw0FbcXwMYchR1WQx6acqChFjrvv/bXEUYsNoaF9lz1xyfoQVvKckjwhqXHA4losmn4EfwyqRNjGjQpNMrDo3FgIqkwHaeHA5iGueXyALybrARIaso6e41Bob74HMB6DkVQ== pcnoordhuis@gmail.com
  - ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDDz32MtEUVESsqwRFiTpNYz+d+bsZunlZ4QY
  /ljq0h65EIg2YrJUJpzVzxN9oBEoo4VsscqGSnVqMiyJ1Da1cvdVlAVVjacPVAnXzu32dEcrYP0XkBpysKrgSc0XqEcCs8sCAbjR9F96oFDVAONBix0gyElGhijqpBbaemql5mpn+0zEQ+c2MNygEUXsl1ImLyP3+HOeiVdabP5FePUby0gX12VZ3aUSKh0t1T5TT9ZU6zYWJhybVrn2Zi27ZdXwx9il182iDIfxXKlQ8rVxjR6vQh9pwSjQ7Fw7xwO/Ra82xn4+Demfz4eOVyvwjUBcLXIzkycDUCK+5XTRRydvZj dcp@photon

coreos:
  units:
    - name: update-engine-stub.service
      command: stop
      mask: true

    - name: update-engine-stub.timer
      command: stop
      mask: true

    - name: update-engine.service
      command: stop
      mask: true

    - name: 00-1st-10gbit-nic.network
      content: |
        [Match]
        Path=pci-0000:41:00.0

        [Network]
        DNS=10.17.131.1
        DNS=10.17.131.2
        Address={{ .Address }}/24
        Gateway={{ .Gateway }}

    - name: 99-other.network
      content: |
        [Match]
        Name=en*

        [Network]
        DHCP=no

    - name: dummy-btrfs@.service
      content: |
        [Unit]
        Description=Create dummy btrfs filesystem at /tmp/%I
        Requires=tmp.mount
        After=tmp.mount

        [Service]
        Type=oneshot
        ExecStartPre=/usr/bin/fallocate -l 4G /tmp/%I
        ExecStart=/usr/sbin/mkfs.btrfs /tmp/%I

    - name: var-lib-docker.mount
      enable: true
      content: |
        [Unit]
        Requires=dummy-btrfs@docker.service
        After=dummy-btrfs@docker.service
        Before=docker.socket

        [Mount]
        What=/tmp/docker
        Where=/var/lib/docker
        Type=btrfs

        [Install]
        RequiredBy=docker.socket

    - name: docker.socket
      command: restart
`

var CoreosCloudConfig = &provisioning.BootConfig{
	Label: "config-2",
	Data: map[string]string{
		"Address": "10.115.9.1",
	},
	Files: []provisioning.FileEntry{
		{
			Path:     "openstack/latest/user_data",
			Contents: cloudConfigTemplateSource,
		},
	},
}
