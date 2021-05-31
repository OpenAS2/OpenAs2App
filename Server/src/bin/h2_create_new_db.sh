#!/bin/bash
set -e
# purpose: creates a new H2 database
x=`basename $0`

function usage() {
           echo "Create a new OpenAS2 H2 database."
           echo "usage: ${x} [-u user ID] <-P password> [-h host] [-p port] [-d db_name]"
           echo "            WHERE"
           echo "               user ID = the user ID configured for the DB Defaults to 'sa'"
           echo "               password = the password configured for the DB"
           echo "                           Can be set as OPENAS2_DB_PWD environment variable"
           echo "               host = hostname or IP address of OpenAS2 server. Defaults to \"localhost\" if not provided."
           echo "               port = port that the OpenAS2 DB is running on. Defaults to 9092 if not provided."
           echo "               db_name = name of the DB. Defaults to 'openas2'" 
           echo ""
           echo "       eg. $0 -u MyuserId -P MySecret"
           echo "           $0 -u MyuserId -P MySecret -h as2.mydomain.com -d tracking_db"
           echo "           $0 -u MyuserId -P MySecret -h as2.mydomain.com -p 9001 -c SSL_DH_anon_WITH_RC4_128_MD5"
           exit 1
}

if test $# -lt 1; then
  usage
fi

OPENAS2_DB_UID=sa
HOST_NAME=localhost
HOST_PORT=9092
DB_NAME=openas2
while getopts "u:p:h:P:d:" opt; do
  case ${opt} in
    u ) OPENAS2_DB_UID=$OPTARG
      ;;
    P ) OPENAS2_DB_PWD=$OPTARG
      ;;
    h ) HOST_NAME=$OPTARG
      ;;
    p ) HOST_PORT=$OPTARG
      ;;
    d ) DB_NAME=$OPTARG
      ;;
    \? )   usage
      ;;
  esac
done

binDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
dbDir=${binDir}/../config/DB
dbFile=${dbDir}/${DB_NAME}

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

#CMD=`echo "${JAVA_HOME}/bin/java -cp ${binDir}/../lib/h2* org.h2.tools.Shell -user ${OPENAS2_DB_UID} -password ${OPENAS2_DB_PWD} -url jdbc:h2:${dbFile}"`
CMD=`echo "${JAVA_HOME}/bin/java -cp ${binDir}/../lib/h2* org.h2.tools.RunScript -user ${OPENAS2_DB_UID} -password ${OPENAS2_DB_PWD} -url jdbc:h2:${dbFile} -script ${binDir}/../config/db_ddl.sql"`
echo
echo Running ${CMD}
echo
  ${CMD}
  RETVAL="$?"
exit $RETVAL
