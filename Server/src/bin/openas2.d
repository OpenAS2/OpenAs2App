#!/bin/bash

### BEGIN INIT INFO
# Provides:          openas2.d
# Required-Start:    
# Required-Stop:     
# Default-Start:     2 3 4 5
# Default-Stop:      0 1 6
# Short-Description: Startup script to launch OpenAS2 application as a daemon
# Description:       This script can be used in any NIX based system that implements the init.d mechanism
#					 The EXECUTABLE variable below must be set to point to the OpenAS2 startup script
#                    See the OpenAS2HowTo.pdf for details on configuration checks for this mode of running OpenAS2
### END INIT INFO

SERVICE_NAME=OpenAS2
EXECUTABLE=/opt/OpenAS2/bin/start-openas2.sh
export PID_FILE=/opt/OpenAS2/bin/OpenAS2.pid
THIS_SCRIPT_NAME=`basename $0`
THIS_SCRIPT_EXEC=$0
if [ "$THIS_SCRIPT_NAME" = "$0" ]; then
  THIS_SCRIPT_EXEC="/etc/init.d/$0"
fi

PID=""
PID_FILE=/opt/OpenAS2/bin/OpenAS2.pid
#PID=$(ps -ef | grep java | grep org.openas2.app.OpenAS2Server | awk '{print $2}')
if [ -f $PID_FILE ]; then
  PID=`cat $PID_FILE`
fi
if [ ! -z $PID ]; then
  x=$(ps -p $PID  2>/dev/null)
  if [ "$?" = 1 ]; then
    PID=""
    echo "" > $PID_FILE
  fi
fi
case "$1" in
    start)
        echo "Starting $SERVICE_NAME ..."
        if [ -z $PID ]; then
          export OPENAS2_AS_DAEMON=true
          $EXECUTABLE
          RETVAL="$?"
	  if [ "$RETVAL" = 0 ]; then 
            echo "$SERVICE_NAME started ..."
            exit 0
          else
            echo "ERROR $SERVICE_NAME could not be started. Review logs"
            exit 1
          fi
        else
                echo "$SERVICE_NAME is already running ..."
        fi
        ;;
    stop|kill)
        if [ ! -z $PID ]; then
          echo "Attempting to stop $SERVICE_NAME..."
          kill $PID
          if [ "$?" = 0 ]; then
            echo "" > $PID_FILE
            echo "$SERVICE_NAME terminated ..."
            exit 0
          else
            echo "ERROR: $SERVICE_NAME failed to terminate. try force-stop"
            exit 1
          fi
        else
            echo "$SERVICE_NAME is not running ..."
            exit 0
        fi
        ;;
    force-stop)
        if [ ! -z $PID ]; then
          echo "Attempting to force termination of $SERVICE_NAME..."
          kill -9 $PID
          if [ "$?" = 0 ]; then
            echo "" > $PID_FILE
            echo "$SERVICE_NAME terminated ..."
            exit 0
          else
            echo "ERROR: $SERVICE_NAME failed to terminate. "
            exit 1
          fi
        else
                echo "$SERVICE_NAME is not running ..."
                exit 0
        fi
        ;;
    status)
        if [ -z $PID ]; then
                echo "$SERVICE_NAME is not running"
        else
                echo "$SERVICE_NAME is running"
        fi
        ;;
    force-reload|restart|reload)
        $THIS_SCRIPT_EXEC stop
        if [ ! -z $PID ]; then
          CNT=0
          while ps -p $PID  2>/dev/null; do
            sleep 1;CNT=$CNT+1;
            CNT=$((CNT+1)); if [ $CNT -ge 5 ]; then break; fi
          done
          if ps -p $PID  2>/dev/null; then
            echo "ERROR: Failed to stop $SERVICE_NAME"
            exit 1
          else
            echo "" > $PID_FILE
            PID=""
          fi
        fi
        $THIS_SCRIPT_EXEC start
        ;;
    *)
      echo "Usage: $0 {start|stop|restart|reload|force-reload}"
      exit 1
      ;;

esac

