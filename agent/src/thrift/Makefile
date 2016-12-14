NAME=thrift
include ../python.mk

../../$(DEVELOP)/site-packages/$(NAME).egg-link:: setup.py
	@echo "$(HEADER)Installing $(DEVELOP) $(NAME) $(NORMAL)"
	$(BIN)/python setup.py install

#disable style and copyright check
CHECK_CMD :=
CHECK_COPYRIGHT :=
