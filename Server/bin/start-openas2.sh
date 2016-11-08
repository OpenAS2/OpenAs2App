#!/bin/bash
# purpose: runs the OpenAS2 application     
x=`basename $0`

binDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
keyStorePwd=$1
PWD_OVERRIDE=""

# Set some of the base system properties for the Java environment and logging
# remove -Dorg.apache.commons.logging.Log=org.openas2.logging.Log if using another logging package    
#
EXTRA_PARMS="-Xms32m -Xmx384m -Dorg.apache.commons.logging.Log=org.openas2.logging.Log"
# For versions of Java that prevent restricted HTTP headers (see documentation for discussion on this)
#EXTRA_PARMS="$EXTRA_PARMS -Dsun.net.http.allowRestrictedHeaders=true"
# Uncomment any of the following for enhanced debug
#EXTRA_PARMS="$EXTRA_PARMS -Dmaillogger.debug.enabled=true"
#EXTRA_PARMS="$EXTRA_PARMS -DlogRxdMsgMimeBodyParts=true"
#EXTRA_PARMS="$EXTRA_PARMS -DlogRxdMdnMimeBodyParts=true"
#EXTRA_PARMS="$EXTRA_PARMS -Djavax.net.debug=SSL"

if [  ! -z $keyStorePwd ]; then
  PWD_OVERRIDE="-Dorg.openas2.cert.Password=$keyStorePwd"
fi
if [ -z $JAVA_HOME ]; then
  OS=$(uname -s)

  if [[ "${OS}" == *Darwin* ]]; then
    # Mac OS X platform
    JAVA_HOME=$(/usr/libexec/java_home)
  elif [[ "${OS}" == *Linux* ]]; then
    # Linux platform
    JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
  elif [[ "${OS}" == *MINGW* ]]; then
    # Windows NT platform
    echo "Windows not supported by this script"
  fi
fi

if [ -z $JAVA_HOME ]; then
  echo "ERROR: Cannot find JAVA_HOME"
  exit 1
fi

LIB_JARS="${binDir}/../lib/h2-1.4.192.jar:${binDir}/../lib/javax.mail.jar:${binDir}/../lib/bcpkix-jdk15on-154.jar:${binDir}/../lib/bcprov-jdk15on-154.jar:${binDir}/../lib/bcmail-jdk15on-154.jar:${binDir}/../lib/commons-logging-1.2.jar:${binDir}/../lib/openas2-server.jar"
JAVA_EXE=$JAVA_HOME/bin/java 
#    
CMD="$JAVA_EXE ${PWD_OVERRIDE} ${EXTRA_PARMS}  -cp .:${LIB_JARS}  org.openas2.app.OpenAS2Server ${binDir}/../config/config.xml"
if [ "true" = "$OPENAS2_AS_DAEMON" ]; then
  $CMD &
else
  $CMD
fi
exit $?