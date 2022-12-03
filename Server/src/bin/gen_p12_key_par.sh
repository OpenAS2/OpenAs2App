#!/bin/bash

x=`basename $0`

if test $# -ne 4; then
  echo "Generate a certificate to a PKCS12 key store."
  echo "You must supply a target key store without the extension (extension will be added as .p12) and an alias for generated certificate."
  echo "usage: ${x} <target keystore> <cert alias> <sigalg> <distinguished name> <start date>"
  echo "            WHERE"
  echo "               target keystore = name of the target keystore file without .p12 extension"
  echo "               cert alias = alias name used to store the created digital certificate in the keystore"
  echo "               sigalg = signing algorithm for the digital certificate ... SHA256, SHA512 etc"
  echo "               distinguished name = a string in the format:"
  echo "                                       CN=<cName>, OU=<orgUnit>, O=<org>, L=<city>, S=<state>, C=<countryCode>"
  echo "            The start date and number of valid daysi for the certificate can be passed in as environment variables:"
  echo "               CERT_START_DATE = date the certificate should be valid from in format \"yyyy/MM/dd [HH:mm:ss]\""
  echo "               CERT_VALID_DAYS = number of days the certificate should be valid for. defaults to 730 days (~2 years)"

  echo ""
  echo "eg.  >export CERT_START_DATE=2022/11/31"
  echo "     >export CERT_VALID_DAYS=365"
  echo "     >$0 as2_certs partnera SHA256 \"CN=as2.partnerb.com, OU=QA, O=PartnerA, L=New York, S=New York, C=US\""
  echo "     Expected OUTPUT: as2_certs.p12 -  keystore containing both public and private key"
  echo "                     partnera.cer - public key certificate file ."
  echo ""
  echo "To run the script without prompts, set environment variables IS_AUTOMATED_EXEC=1 and KEYSTORE_PASSWORD to the desired password (can be blank)"
  echo ""
  exit 1
fi

tgtStore=$1
certAlias=$2
sigAlg="$3withRSA"
dName=$4

if [ -z $CERT_VALID_DAYS ]; then
  CertValidDays=730
else
  CertValidDays=$CERT_VALID_DAYS
fi
AdditionalGenArgs=""
if [ -n "$CERT_START_DATE" ]; then
  AdditionalGenArgs="-startdate $CERT_START_DATE "
  PRE_GEN_MSG_ADDITIONAL=" with a start date of $CERT_START_DATE"
fi

if [ -z $JAVA_HOME ]; then
  baseDir=`dirname $0`
  . ${baseDir}/find_java
fi
if [ -z $JAVA_HOME ]; then
  echo "ERROR: Cannot find JAVA_HOME"
  exit 1
fi

echo "Using JAVA_HOME: ${JAVA_HOME}"
if [ "1" != "$IS_AUTOMATED_EXEC" ]; then
  echo "Generate a certificate to a PKCS12 key store."
  echo "Generating certificate:  using alias $certAlias to ${tgtStore}.p12 $PRE_GEN_MSG_ADDITIONAL"
  read -p "Do you wish to execute this request? [Y/N]" Response
  if [  $Response != "Y" -a $Response != "y"  ] ; then
    exit 1
  fi
  read -p "Enter password for keystore:" ksPwd
else
  ksPwd=$KEYSTORE_PASSWORD
fi

$JAVA_HOME/bin/keytool -genkeypair -alias $certAlias -validity $CertValidDays  -keyalg RSA -sigalg $sigAlg -keystore ${tgtStore}.p12 -storepass "$ksPwd" -storetype pkcs12 $AdditionalGenArgs -dname "$dName"
if [ "$?" != 0 ]; then
	echo ""
    echo "Failed to create a keystore. See errors above to correct the problem."
    exit 1
fi

#$JAVA_HOME/bin/keytool -selfcert -alias $certAlias -validity $CertValidDays  -sigalg $sigAlg -keystore ${tgtStore}.p12 -storepass $ksPwd -storetype pkcs12
$JAVA_HOME/bin/keytool -selfcert -alias $certAlias $AdditionalGenArgs -validity $CertValidDays  -sigalg $sigAlg -keystore ${tgtStore}.p12 -storepass "$ksPwd" -storetype pkcs12
if [ "$?" != 0 ]; then
	echo ""
    echo "Failed to self certifiy the certificates in the keystore. See errors above to correct the problem."
    exit 1
fi

$JAVA_HOME/bin/keytool -export -rfc -file $certAlias.cer -alias $certAlias  -keystore ${tgtStore}.p12 -storepass "$ksPwd" -storetype pkcs12
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
