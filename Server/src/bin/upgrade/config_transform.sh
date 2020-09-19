#!/bin/sh
#
#

x=`basename $0`
if test $# -lt 1; then
echo "This script generates an upgraded version of your current config.xml pre version 2.10.0"
echo "You must supply the current config.xml file including path."
echo"The output will default to a file named config.xml.new"
echo "usage: ${x} <source config file> [output file named>]"
echo "       eg. $0 /opt/OpenAS2/config/config.xml      --- will produce a file named config.xml.new in the current directory"
echo "           $0 /opt/OpenAS2/config/config.xml   config.xml   --- will produce a file named config.xml in the current directory"
exit 1
fi
scriptDir=`dirname $0`
configFile=$1
outFile=$2

if [ "x" = "x$outFile" ]; then
outFile="config.xml.new"
fi
xsltFile="$scriptDir/config.xslt"

echo ""
echo "          IMPORTANT NOTICE!!!!"
echo "          IMPORTANT NOTICE!!!!"
echo "     This script may disable the email logger and HTTPS sender modules if you had them enabled."
echo ""
echo "         YOU WILL NEED TO ADD THIS TO ALL  AS2DirectoryPollingModule modules: enabled=\"true\""
echo ""
echo "     Please verify the modules you want are enabled in the \"properties\" element of the ${configFile}"
echo "Generating new config to file: $outFile ..."
java -jar $scriptDir/lib/saxon9he.jar -xsl:${scriptDir}/${xsltFile}  -o:$outFile -s:${configFile}
echo "Done"
exit 0
