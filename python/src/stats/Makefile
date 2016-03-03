

ifdef REMOTE_SERVER
	TEST_OPTS += --tc=remote_servers:$(REMOTE_SERVER)
endif
EXTRAS=[test,esx]
NAME=photon.controller.stats
DEPS=common
TESTS=stats/tests/unit
include ../python.mk
