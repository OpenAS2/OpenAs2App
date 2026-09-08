#!/bin/bash
# purpose: migrates a partnerships XML file into the partner and partnership database tables
#          read by org.openas2.partner.DbPartnershipFactory
set -e

x=`basename $0`
scriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if test $# -lt 1; then
  echo "Migrates a partnerships XML file into the OpenAS2 partnership database tables so the"
  echo "partnerships component can be switched from XMLPartnershipFactory to DbPartnershipFactory."
  echo ""
  echo "usage: ${x} <partnerships.xml> <jdbc_url> <db_user> <db_password> [options]"
  echo "       ${x} <partnerships.xml> --dry-run"
  echo ""
  echo "options:"
  echo "  --dry-run              Parse and validate the file and print what would be written. Changes nothing."
  echo "  --replace              Delete the partner and partnership rows already in the database first."
  echo "                         Without this the migration aborts if either table has any rows."
  echo "  --jdbc-driver=<class>  Load this JDBC driver class before connecting. Only needed for a"
  echo "                         driver that does not register itself automatically."
  echo ""
  echo "The tables must already exist. Create them with the partner and partnership section of"
  echo "config/db_ddl.sql before running this. Copy out just that section: running the whole file"
  echo "drops the msg_metadata message tracking table."
  echo ""
  echo "A driver for your database must be on the classpath. The bundled H2 driver is already in"
  echo "lib. For anything else, put the driver jar in the lib directory alongside it."
  echo ""
  echo "examples:"
  echo "  ${x} ../config/partnerships.xml --dry-run"
  echo "  ${x} ../config/partnerships.xml \"jdbc:h2:../data/openas2\" sa OpenAS2"
  exit 1
fi

# Locate a JVM the same way the server start script does
if [ -z $JAVA_HOME ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME=$(/usr/libexec/java_home)
  elif [ -n "$(command -v java)" ]; then
    JAVA_HOME=$(dirname $(dirname $(readlink -f $(command -v java))))
  fi
fi
if [ -z $JAVA_HOME ]; then
  echo "ERROR: Cannot find JAVA_HOME. Set it and re-run."
  exit 1
fi

# This script lives in bin/upgrade so the application jars are two levels up
CLASSPATH=$(echo "${scriptDir}/../../lib/"*".jar" | tr ' ' ':')

exec "${JAVA_HOME}/bin/java" -cp ".:${CLASSPATH}" org.openas2.upgrades.MigratePartnershipsToDb "$@"
