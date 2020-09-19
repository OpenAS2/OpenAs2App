#!/bin/bash
set -e
# purpose: runs the OpenAS2 application     
x=`basename $0`

binDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

EXTRA_PARMS="$@"

PWD_OVERRIDE=""

# Backwards compatibility: use value from pid_file if pid_file has a value and openas2_pid has no value.
#
if [ -n "$PID_FILE" ] && [ -z "$OPENAS2_PID" ]; then
  export OPENAS2_PID=$PID_FILE
fi

if [ -z "$OPENAS2_PID" ]; then
  export OPENAS2_PID=$binDir/OpenAS2.pid
fi

# Set some of the base system properties for the Java environment and logging
# remove -Dorg.apache.commons.logging.Log=org.openas2.logging.Log if using another logging package
#
EXTRA_PARMS="$EXTRA_PARMS -Xms32m -Xmx384m -Dorg.apache.commons.logging.Log=org.openas2.logging.Log"

# Set the config file location
if [ -z $OPENAS2_CONFIG_FILE ]; then
  OPENAS2_CONFIG_FILE=${binDir}/../config/config.xml
fi
EXTRA_PARMS="$EXTRA_PARMS -Dopenas2.config.file=${OPENAS2_CONFIG_FILE}"

# For versions of Java that prevent restricted HTTP headers (see documentation for discussion on this)
#EXTRA_PARMS="$EXTRA_PARMS -Dsun.net.http.allowRestrictedHeaders=true"

# When using old (unsecure) certificates (please replace them!) that fail to load from the certificate store.
#EXTRA_PARMS="$EXTRA_PARMS -Dorg.bouncycastle.asn1.allow_unsafe_integer=true"

#EXTRA_PARMS="$EXTRA_PARMS -Dhttps.protocols=TLSv1.2"

# Uncomment any of the following for enhanced debug
#EXTRA_PARMS="$EXTRA_PARMS -Dmaillogger.debug.enabled=true"
#EXTRA_PARMS="$EXTRA_PARMS -DlogRxdMsgMimeBodyParts=true"
#EXTRA_PARMS="$EXTRA_PARMS -DlogRxdMdnMimeBodyParts=true"
#EXTRA_PARMS="$EXTRA_PARMS -Djavax.net.debug=SSL"

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
    echo "Writing PID $PID to file $OPENAS2_PID"
    echo $PID > $OPENAS2_PID
  fi
else
  ${CMD}
  RETVAL="$?"
fi
exit $RETVAL