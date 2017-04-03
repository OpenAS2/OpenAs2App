#!/bin/bash
set -e
# purpose: runs the OpenAS2 application     
x=`basename $0`

binDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
keyStorePwd=$1
PWD_OVERRIDE=""

if [ -z $PID_FILE ]; then
  export PID_FILE=$binDir/OpenAS2.pid
fi

# Set some of the base system properties for the Java environment and logging
# remove -Dorg.apache.commons.logging.Log=org.openas2.logging.Log if using another logging package    
#
EXTRA_PARMS="-Xms32m -Xmx384m -Dorg.apache.commons.logging.Log=org.openas2.logging.Log"

# Set the config file location
EXTRA_PARMS="$EXTRA_PARMS -Dopenas2.config.file=${binDir}/../config/config.xml"

# For versions of Java that prevent restricted HTTP headers (see documentation for discussion on this)
#EXTRA_PARMS="$EXTRA_PARMS -Dsun.net.http.allowRestrictedHeaders=true"

#EXTRA_PARMS="$EXTRA_PARMS -Dhttps.protocols=TLSv1.2"

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

CMD=`echo "${JAVA_HOME}/bin/java ${PWD_OVERRIDE} ${EXTRA_PARMS} -cp .:${binDir}/../lib/* org.openas2.app.OpenAS2Server"`
echo
echo Running ${CMD}
echo
if [ "true" = "$OPENAS2_AS_DAEMON" ]; then
  $CMD &
  RETVAL="$?"
  PID=$!
  if [ "$RETVAL" = 0 ]; then
    echo "Writing PID $PID to file $PID_FILE"
    echo $PID > $PID_FILE
  fi
else
  ${CMD}
  RETVAL="$?"
fi
exit $RETVAL