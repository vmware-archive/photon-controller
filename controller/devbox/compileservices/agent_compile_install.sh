#!/bin/bash -xe


export TERM="vt100"

#
# Point to the openly available vibauthor
#
sed -i 's/$BINARIES\/bin\/vibauthor/vibauthor/' /devbox_data/python/vib/create_vib.sh

#
# Compile agent
#
cd /devbox_data/python && make clean dist && make vib-only
