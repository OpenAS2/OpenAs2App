#!/bin/sh
# purpose: runs the OpenAS2 application     
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
  exit
fi
JAVA_EXE=$JAVA_HOME/bin/java 
#    
# remove -Dorg.apache.commons.logging.Log=org.openas2.logging.Log if using another logging package    
#
$JAVA_EXE -Xms32m -Xmx384m -Dorg.apache.commons.logging.Log=org.openas2.logging.Log  -cp .:../lib/javax.mail.jar:../lib/bcpkix-jdk15on-152.jar:../lib/bcprov-jdk15on-152.jar:../lib/bcmail-jdk15on-152.jar:../lib/bcprov-jdk15on-152:../lib/commons-logging-1.2.jar:../lib/openas2-server.jar org.openas2.app.OpenAS2Server ../config/config.xml
