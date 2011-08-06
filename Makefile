#
# Running make is only necessary when first setting up, or:
#  - when you've added any new dependencies (by editing conf/dependencies.yml)
#  - when you've changed netlogo/RunHeadless.java (because it doesn't get re-compiled automatically by Play)
#
# Running make will NOT compile the actual application code. This is done automatically
# by Play when you run the app (after the first HTTP request).
#
# This Makefile exists only as a temporary solution until we figure out something better.
#

# Path to NetLogo's dependencies (asm-3.3.1.jar, gluegen-rt-1.1.1.jar, picocontainer-2.11.1.jar, and so on...)
# Include the trailing slash
NETLOGO_DEPS = "/home/serg/netlogo/nlogo/lib_managed/scala_2.9.0-1/compile/*"

# Path to scala-library.jar (version 2.9.0-1)
SCALA_LIB_JAR = /home/serg/netlogo/nlogo/project/boot/scala-2.9.0-1/lib/scala-library.jar

JAVA = java
JAVAC = javac
JFLAGS = -classpath NetLogo.jar:$NETLOGO_DEPS:$SCALA_LIB_JAR

prereqs: playdeps copyjars netlogo/RunHeadless.class

playdeps:
	play deps
	
copyjars:
	mkdir -p public/jars
	cp netlogo/NetLogo.jar public/jars/NetLogo.jar
	cp $(SCALA_LIB_JAR) public/jars/scala-library.jar

netlogo/RunHeadless.class: netlogo/RunHeadless.java
	cd netlogo && $(JAVAC) $(JFLAGS) RunHeadless.java

