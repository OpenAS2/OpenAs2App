#!/bin/bash

x=`basename $0`
relBinDir=`dirname $0`
binDir=`realpath $relBinDir`
srcBaseDir=`realpath $binDir/../..`
configDir=${srcBaseDir}/src/config

cd $configDir
if [ "$?" != 0 ]; then
    echo "******  Failed to find the "config" directory. See errors above to correct the problem."
    exit 1
fi
export IS_AUTOMATED_EXEC=1
export KEYSTORE_PASSWORD=testas2
echo "************"
echo "Deleting existing keystores..."
alias1=mycompany
alias2=partnera
alias3=partnerb
ks1=as2_certs.p12
ks2=${alias2}_certs.p12
ks3=${alias2}_certs.p12
rm -f $ks1 $ks2 $ks3
$binDir/gen_p12_key_par.sh ${ks1%.*} ${alias1} SHA256 'CN=as2.${alias1}.com, OU=QA, O=MyCompany, L=Cape Town, S=Western Cape, C=ZA'
if [ "$?" != 0 ]; then
    echo "******  Failed to create as2_certs.p12 keystore. See errors above to correct the problem."
    exit 1
fi
$binDir/gen_p12_key_par.sh ${ks2%.*} ${alias2} SHA256 'CN=as2.${alias2}.com, OU=QA, O=PartnerA, L=New York, S=New York, C=US'
if [ "$?" != 0 ]; then
    echo "******  Failed to create ${ks2} keystore. See errors above to correct the problem."
    exit 1
fi
$binDir/gen_p12_key_par.sh ${ks3%.*} ${alias3} SHA256 'CN=as2.${alias3}.com, OU=QA, O=PartnerB, L=London, S=London, C=US'
if [ "$?" != 0 ]; then
    echo "******  Failed to create ${ks3} keystore. See errors above to correct the problem."
    exit 1
fi
$binDir/import_alias_from_keystore.sh $ks2 ${alias2} $ks1 ${alias2}
if [ "$?" != 0 ]; then
    echo "******  Failed to import ${ks2} keystorei into main keystore. See errors above to correct the problem."
    exit 1
fi
$binDir/import_alias_from_keystore.sh $ks3 ${alias3} $ks1 ${alias3}
if [ "$?" != 0 ]; then
    echo "******  Failed to import ${ks3} keystorei into main keystore. See errors above to correct the problem."
    exit 1
fi
rm -f ${alias1}* ${alias2}* ${alias3}*
exit 0
