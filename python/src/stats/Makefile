

ifdef REMOTE_SERVERS
	TEST_OPTS += --tc=remote_servers:$(REMOTE_SERVERS)
endif
EXTRAS=[test,esx]
NAME=photon.controller.stats
DEPS=common pysdk agent host
# TESTS=stats/tests/unit
include ../python.mk