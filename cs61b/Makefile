CHECKSTYLE_VERSION = 9.2.1

OTHER_CLASSES = $(shell pwd)/../target/checkstyle-$(CHECKSTYLE_VERSION)-all.jar

RESOURCES = $(shell pwd)/../src/main/resources

UCB_SRC = $(shell find ../src/main/java/ucb -name '*.java')

COM_SRC = $(filter-out %/CheckstyleAntTask.java, $(shell find ../src/main/java/com -name '*.java'))

JFLAGS = -g

ifndef LIBDIR
    ifdef INSTALLDIR
        LIBDIR = $(INSTALLDIR)/lib
    endif
endif

.PHONY: default alt-compile build clean

default: alt-compile

alt-compile: classes $(OTHER_CLASSES)
	javac -cp $(OTHER_CLASSES):classes -d classes $(JFLAGS) $(UCB_SRC) $(COM_SRC)
	rsync -a $(RESOURCES)/ classes

build:
	(cd ..; mvn -P assembly package)

install:
	@if [ -z "$(INSTALLDIR)" -a -z "$(LIBDIR)" ]; then \
               echo "Must specify INSTALLDIR or LIBDIR"; false; \
        elif [ -z "$(LIBDIR)" ]; then \
               export LIBDIR=$(INSTALLDIR)/lib; \
        fi
	cp ../target/checkstyle-$(CHECKSTYLE_VERSION)-all.jar $(LIBDIR)/checkstyle/ucb-checkstyle.jar

classes:
	mkdir $@

