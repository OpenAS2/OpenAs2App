#!/bin/bash
set -e
# purpose: runs the remote OpenAS2 connect application     
x=`basename $0`

function usage() {
           echo "Connect to a running instance of OpenAS2."
           echo "usage: ${x} <-u user ID> <-P password> [-h host] [-p port] [-c cipher]"
           echo "            WHERE"
           echo "               user ID = the user ID configured for the socket command processor module"
           echo "               password = the password configured for the socket command processor module"
           echo "                           Can be set as OPENAS2_SOCKET_PWD environment variable"
           echo "               host = hostname or IP address of OpenAS2 server. Defaults to \"localhost\" if not provided."
           echo "               port = port that the OpenAS2 socket command processor is running on. Defaults to 14321 if not provided."
           echo "               cipher = anonymous cipher for the OpenAS2 socket command processor connection. Defaults to whatever is in the compiled jar if not provided."
           echo ""
           echo "       eg. $0 -u MyuserId -p MySecret"
           echo "           $0 -u MyuserId -p MySecret -h as2.mydomain.com"
           echo "           $0 -u MyuserId -p MySecret -h as2.mydomain.com -c SSL_DH_anon_WITH_RC4_128_MD5"
           exit 1
}

if test $# -lt 2; then
  usage
fi

HOST_NAME=localhost
HOST_PORT=14321

while getopts "u:p:h:P:" opt; do
  case ${opt} in
    u ) OPENAS2_SOCKET_UID=$OPTARG
      ;;
    P ) OPENAS2_SOCKET_PWD=$OPTARG
      ;;
    h ) HOST_NAME=$OPTARG
      ;;
    p ) HOST_PORT=$OPTARG
      ;;
    c ) CIPHER=$OPTARG
      ;;
    \? )   usage
      ;;
  esac
done

if [ ! -z $CIPHER ]; then
	SET_CIPHER="-DCmdProcessorSocketCipher=$CIPHER"
else
    SET_CIPHER=""
fi
binDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Backwards compatibility: use value from pid_file if pid_file has a value and openas2_pid has no value.
#
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

CMD=`echo "${JAVA_HOME}/bin/java ${SET_CIPHER} -cp .:${binDir}/remote/* org.openas2.remote.CommandLine ${HOST_NAME} ${HOST_PORT} ${OPENAS2_SOCKET_UID} ${OPENAS2_SOCKET_PWD}"`
echo
echo Running ${CMD}
echo
  ${CMD}
  RETVAL="$?"
exit $RETVAL