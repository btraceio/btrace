BUILDDIR=../build
SRCDIR=../src
OUTDIR=${BUILDDIR}/`uname -p`

# FIXME: 64 bit option

all:
	${JAVA_HOME}/bin/javah -classpath ${BUILDDIR}/btrace-boot.jar -d ${OUTDIR} com.sun.btrace.BTraceRuntime
	mkdir -p ${OUTDIR}
	cc -c -I${JAVA_HOME}/include -I${JAVA_HOME}/include/solaris -I${OUTDIR} -o ${OUTDIR}/btrace.o ${SRCDIR}/solaris/native/btrace.c
	/usr/sbin/dtrace -G -o ${OUTDIR}/btraced.o -s ${SRCDIR}/solaris/native/btraced.d ${OUTDIR}/btrace.o
	cc -G ${OUTDIR}/btrace.o ${OUTDIR}/btraced.o -o ${OUTDIR}/libbtrace.so

clean:
	rm -rf ${OUTDIR}
