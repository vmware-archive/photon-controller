OS := $(shell uname)
THRIFT=thrift
THRIFT_GEN=$(THRIFT) -r --gen py:new_style -out .
THRIFT_SRC=$(shell find ../../../thrift -type f -name \*.thrift)
THRIFT_PY= \
  gen/common/__init__.py \
  gen/flavors/__init__.py \
  gen/host/__init__.py \
  gen/scheduler/__init__.py \
  gen/test/echoer/__init__.py \
  gen/statsd/__init__.py \
  gen/stats_plugin/__init__.py \
  gen/agent/__init__.py \

CHECK_PRUNE='gen'
EXTRAS=
EXTRA_PY_TARGETS=$(THRIFT_PY)
EXTRA_CLEANUP_TARGETS=gen

include ../python.mk

define compile_thrift
	$(THRIFT_GEN) $(1)
	rm -f __init__.py
endef

gen/host/__init__.py: $(THRIFT_SRC)
	$(call compile_thrift,../../../thrift/host.thrift)

gen/agent/__init__.py: $(THRIFT_SRC)
	$(call compile_thrift,../../../thrift/agent.thrift)

gen/scheduler/__init__.py: $(THRIFT_SRC)
	$(call compile_thrift,../../../thrift/scheduler.thrift)

gen/common/__init__.py: $(THRIFT_SRC)
	$(call compile_thrift,../../../thrift/server_address.thrift)

gen/flavors/__init__.py: $(THRIFT_SRC)
	$(call compile_thrift,../../../thrift/flavors.thrift)

gen/test/echoer/__init__.py: thrift/echo.thrift
	$(call compile_thrift,thrift/echo.thrift)

gen/stats_plugin/__init__.py: $(THRIFT_SRC)
	$(call compile_thrift,../../../thrift/stats_plugin.thrift)

gen/statsd/__init__.py: $(THRIFT_SRC)
	$(call compile_thrift,../../../thrift/statsd/statsd.thrift)
