#!/bin/sh

x=`basename $0`
if test $# -lt 3; then
  echo "Import a public certificate to a PKCS12 key store."
  echo "You must specify the source file, target key store file name and an alias for imported certificate."
  echo "By default the script will attemopt to import the designated certificate."
  echo "If you wish to replace an existing certificate then specify "replace" as a 4th argument to the script"
  echo "usage: ${x} <src certificate> <target keystore> <cert alias> [action]"
  echo "            WHERE"
  echo "               src certificate = name of the file containg the public key to be imported"
  echo "               target keystore = name of the target keystore file including .p12 extension"
  echo "               cert alias = alias name used to store the created digital certificate in the keystore"
  echo "               action = if not provided this defaults to \"import\". The only other option is \"replace\""
  echo "                         anything other than \"replace\" will be interpreted as \"import\""

  echo ""
  echo "       eg. $0 partnera.cer as2_certs.p12 partnera"
  echo "                OR"
  echo "       eg. $0 partnera.cer as2_certs.p12 partnera replace"
  exit 1
fi

srcFile=$1
tgtStore=$2
certAlias=$3
action=$4

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

echo "Executing action \"${action}\" on certificate from file \"${srcFile}\" using alias \"${certAlias}\" to: ${tgtStore}"
read -p "Do you wish to execute this request? [Y/N]" Response
if [  $Response != "Y" -a $Response != "y"  ] ; then
  exit 1
fi

read -p "Enter password for keystore:" ksPwd

if [ "${action}" = "replace" ]; then
    $JAVA_HOME/bin/keytool -delete -alias ${certAlias} -keystore ${tgtStore} -storepass $ksPwd -storetype pkcs12
    if [ "$?" != 0 ]; then
    	echo ""
        echo "Failed to delete the certificate in the keystore for alias \"${certAlias}\". See errors above to correct the problem."
        exit 1
    fi
fi
$JAVA_HOME/bin/keytool -importcert -file ${srcFile} -alias ${certAlias} -keystore ${tgtStore} -storepass $ksPwd -storetype pkcs12
if [ "$?" != 0 ]; then
	echo ""
    echo "***** Failed to import the certificate to the keystore. See errors above to correct the problem."
    echo "      If the error shows the certifcate already eists then add the \"replace\" option to the command line."
    exit 1
fi

echo ""
echo "  Sucessfully Imported certificate from file \"${srcFile}\" using alias \"${certAlias}\" to: ${tgtStore}"
echo ""
