#!/bin/sh

CertValidDays=3650
x=`basename $0`
if test $# -ne 4; then
  echo "Generate a certificate to a PKCS12 key store."
  echo "You must supply a target key store without the extension (extension will be added as .p12) and an alias for generated certificate."
  echo "usage: ${x} <target keystore> <cert alias> <sigalg> <distinguished name>"
  echo "            WHERE"
  echo "               target keystore = name of the target keystore file without .p12 extension"
  echo "               cert alias = alias name used to store the created digital certificate in the keystore"
  echo "               sigalg = signing algorithm for the digital certificate ... SHA256, SHA512 etc"
  echo "               distinguished name = a string in the format:"
  echo "                                       CN=<cName>, OU=<orgUnit>, O=<org>, L=<city>, S=<state>, C=<countryCode>"

  echo ""
  echo "       eg. $0 as2_certs partnera SHA256 \"CN=as2.partnerb.com, OU=QA, O=PartnerA, L=New York, S=New York, C=US\""
  echo "     Expected OUTPUT: as2_certs.p12 -  keystore containing both public and private key"
  echo "                     partnera.cer - public key certificate file ."
  exit 1
fi

tgtStore=$1
certAlias=$2
sigAlg="$3withRSA"
dName=$4

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

echo "Generate a certificate to a PKCS12 key store."
echo "Generating certificate:  using alias $certAlias to ${tgtStore}.p12"
read -p "Do you wish to execute this request? [Y/N]" Response
if [  $Response != "Y" -a $Response != "y"  ] ; then
  exit 1
fi

read -p "Enter password for keystore:" ksPwd
$JAVA_HOME/bin/keytool -genkeypair -alias $certAlias -validity $CertValidDays  -keyalg RSA -sigalg $sigAlg -keystore ${tgtStore}.p12 -storepass $ksPwd -storetype pkcs12 -dname "$dName"
if [ "$?" != 0 ]; then
	echo ""
    echo "Failed to create a keystore. See errors above to correct the problem."
    exit 1
fi

$JAVA_HOME/bin/keytool -selfcert -alias $certAlias -validity $CertValidDays  -sigalg $sigAlg -keystore ${tgtStore}.p12 -storepass $ksPwd -storetype pkcs12
if [ "$?" != 0 ]; then
	echo ""
    echo "Failed to self certifiy the certificates in the keystore. See errors above to correct the problem."
    exit 1
fi

$JAVA_HOME/bin/keytool -export -rfc -file $certAlias.cer -alias $certAlias  -keystore ${tgtStore}.p12 -storepass $ksPwd -storetype pkcs12
if [ "$?" != 0 ]; then
	echo ""
    echo "Failed to export the public key. See errors above to correct the problem."
    exit 1
fi

echo ""
echo "Generated files:"
echo "     PKCS12 keystore: ${tgtStore}.p12"
echo "     Public Key File: ${certAlias}.cer"
echo ""
