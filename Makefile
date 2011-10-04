#
# Running make is only necessary when first setting up, or:
#  - when you've added any new dependencies (by editing conf/dependencies.yml)
#  - when you've changed netlogo/RunHeadless.java (because it doesn't get re-compiled automatically by Play)
#
# Running make will NOT compile the actual application code. This is done automatically
# by Play when you run the app (after the first HTTP request).
#
# The settings in the Config section below may need to be modified to reflect your environment.
#
# This Makefile should run on Linux and Windows (using Cygwin). When using Cygwin, you
# should generally use Cygwin's path conventions (start paths with /cygdrive/c/ instead of
# C:\ and use forward slashes) except where noted otherwise.  Additionally, you will need
# to install GNU make and python through Cygwin's setup utility (for Java, the native
# Windows installation of the JDK will suffice).
#

##############
#   Config   #
##############

# 
# Set the paths to 'java' and 'javac' here. Java JDK version 1.6.0_26 is recommended.
# If both of these programs are on the PATH already, then simply set these to "java"
# and "javac".
#    Linux example: "/usr/lib/jvm/jdk1.6.0_26/bin/java"
#    Cygwin example: "/cygdrive/c/Program Files (x86)/Java/jdk1.6.0_26/bin/java"
JAVA = "java"
JAVAC = "javac"

# Set the path to the 'play' script from the Play Framework installation here (version 1.2.2
# is recommended). If using Cygwin on Windows, be sure to specify the path to the 'play.bat'
# script (instead of the 'play' script, which only works on Linux) using a Cygwin-style path
# (beginning with /cygdrive). If the Play Framework directory is on the PATH, then simply set
# this to either 'play' (on Linux) or 'play.bat' (on Windows).
#    Linux example: "/home/serg/play-1.2.2/play"
#    Cygwin example: "/cygdrive/c/Users/serg/play-1.2.2/play.bat"
PLAY = "play"

# Set the paths to NetLogo's dependencies here (asm-3.3.1.jar, gluegen-rt-1.1.1.jar,
# picocontainer-2.11.1.jar, and so on...). You can get these by cloning NetLogo's repository
# and running 'sbt update'. To include all of NetLogo's dependencies without having to list
# them individually, end the path using /*
#    Linux Example:  "/home/serg/netlogo/lib_managed/scala_2.9.0-1/compile/*"
#    Cygwin Example: "/cygdrive/c/Users/serg/NetLogo/lib_managed/scala_2.9.0-1/compile/*"
NETLOGO_DEPS = "/home/serg/netlogo/nlogo/lib_managed/scala_2.9.0-1/compile/*"

# Set the path to scala-library.jar here (version 2.9.0-1 is required).
#    Linux Example:  "/home/serg/netlogo/project/boot/scala-2.9.0-1/lib/scala-library.jar"
#    Cygwin Example: "/cygdrive/c/Users/serg/NetLogo/project/boot/scala-2.9.0-1/lib/scala-library.jar"
SCALA_LIB_JAR = "/home/serg/netlogo/nlogo/project/boot/scala-2.9.0-1/lib/scala-library.jar"


#########
# Setup #
#########

# Test whether we are running inside Cygwin
OS_VERSION:=$(shell uname)
ifneq (,$(findstring CYGWIN, $(OS_VERSION)))
	cygwin=true
else
	cygwin=false
endif

# Java flags:
# When passing arguments to javac in Cygwin, we need to convert the Cygwin-style paths to
# Windows-style paths. Elsewhere in the Makefile, we want to keep these as Cygwin-style paths.
ifeq ($(cygwin), true)
	JFLAGS = -classpath `cygpath -wp NetLogo.jar:$(NETLOGO_DEPS):$(SCALA_LIB_JAR)`
else
	JFLAGS = -classpath NetLogo.jar:$(NETLOGO_DEPS):$(SCALA_LIB_JAR)
endif


###########
# Targets #
###########

all: playdeps copyjars netlogo/RunHeadless.class

playdeps:
	$(PLAY) deps

copyjars:
	mkdir -p public/jars
	cp netlogo/NetLogo.jar public/jars/NetLogo.jar
	cp $(SCALA_LIB_JAR) public/jars/scala-library.jar

netlogo/RunHeadless.class: netlogo/RunHeadless.java
	cd netlogo && $(JAVAC) $(JFLAGS) RunHeadless.java

