#!/bin/sh

x=`basename $0`
if test $# -lt 3; then
  echo "Import an entry in a source PKCS12 keystore identified by an alias to a target PKCS12 key store."
  echo "You must specify the source keystore, source alias entry, target key store file name and an alias for imported certificate."
  echo "By default the script will attempt to import the designated entries in the specified alias."
  echo "If you wish to replace an existing entry in the target keystore then specify "replace" as a 4th argument to the script"
  echo "usage: ${x} <src keystore> <src alias> <target keystore> <target alias> [action]"
  echo "            WHERE"
  echo "               src keystore = name of the keystore containing the entry to be imported"
  echo "               src alias = name of the alias in the source keystore to be imported"
  echo "               target keystore = name of the target keystore file including .p12 extension"
  echo "               target alias = alias name used to store the imported entry in the keystore"
  echo "               action = if not provided this defaults to \"import\". The only other option is \"replace\""
  echo "                         anything other than \"replace\" will be interpreted as \"import\""

  echo ""
  echo "       eg. $0 my_cert2.p12 my_cert as2_certs.p12 my_cert_2"
  echo "                OR"
  echo "       eg. $0 my_cert2.p12 my_cert as2_certs.p12 my_cert_2 replace"
  exit 1
fi

srcKeystore=$1
srcAlias=$2
tgtKeystore=$3
tgtAlias=$4
action=$5

if [ -z $JAVA_HOME ]; then
  baseDir=`dirname $0`
  . ${baseDir}/find_java
fi

if [ -z $JAVA_HOME ]; then
  echo "ERROR: Cannot find JAVA_HOME"
  exit 1
fi

echo "Executing action \"${action}\" on certificate from key \"${srcKeystore}\" using alias \"${tgtAlias}\" to: ${tgtKeystore}"
if [ "1" != "$IS_AUTOMATED_EXEC" ]; then
  read -p "Do you wish to execute this request? [Y/N]" Response
  if [  $Response != "Y" -a $Response != "y"  ] ; then
    exit 1
  fi
  read -p "Enter password for source keystore:" srcksPwd
  read -p "Enter password for destination keystore:" destksPwd
else
  srcksPwd=${KEYSTORE_PASSWORD}
  destksPwd=${KEYSTORE_PASSWORD}
fi


if [ "${action}" = "replace" ]; then
    $JAVA_HOME/bin/keytool -delete -alias ${tgtAlias} -keystore ${tgtKeystore} -storepass $destksPwd -storetype pkcs12
    if [ "$?" != 0 ]; then
    	echo ""
    	echo "The REPLACE option was specified."
        echo "Failed to delete the certificate in the keystore for alias \"${tgtAlias}\". See errors above to correct the problem."
        exit 1
    fi
fi
$JAVA_HOME/bin/keytool -importkeystore -srckeystore ${srcKeystore} -srcstoretype pkcs12 -srcstorepass $srcksPwd -srcalias $srcAlias -destalias ${tgtAlias} -destkeystore ${tgtKeystore} -deststorepass $destksPwd -deststoretype pkcs12
if [ "$?" != 0 ]; then
	echo ""
    echo "***** Failed to import the certificate to the keystore. See errors above to correct the problem."
    echo "      If the error shows the certificate already exists then add the \"replace\" option to the command line."
    exit 1
fi

echo ""
echo "  Successfully Imported certificate from file \"${srcKeystore}\" using alias \"${tgtAlias}\" to: ${tgtKeystore}"
echo ""
