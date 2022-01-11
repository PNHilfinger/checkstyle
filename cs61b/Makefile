CHECKSTYLE_VERSION = 9.2.1

OTHER_CLASSES = $(shell pwd)/../target/checkstyle-$(CHECKSTYLE_VERSION)-all.jar

UCB_SRC = $(shell find ../src/main/java/ucb -name '*.java')

COM_SRC = $(filter-out %/CheckstyleAntTask.java, $(shell find ../src/main/java/com -name '*.java'))

.PHONY: default alt-compile build clean

default: alt-compile

alt-compile: classes $(OTHER_CLASSES)
	javac -cp $(OTHER_CLASSES):classes -d classes $(JFLAGS) $(UCB_SRC) $(COM_SRC)

build:
	(cd ..; mvn -P assembly package)

classes:
	mkdir $@

