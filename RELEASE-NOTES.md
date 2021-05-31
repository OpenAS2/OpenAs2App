#              OpenAS2 Server
#              Version 2.12.0
#              RELEASE NOTES
-----
The OpenAS2 project is pleased to announce the release of OpenAS2 2.12.0

The release download file is: OpenAS2Server-2.12.0.zip

The zip file contains a PDF document (OpenAS2HowTo.pdf) providing information on installing and using the application.

Version 2.12.0 - 2021-04-04
This is a minor enhancement release and bugfix:
       **IMPORTANT NOTE**: Please review upgrade notes below if you are upgrading

  1. Upgrade libraries to latest release versions (IMPORTANT: See release notes below for this upgrade)
  2. Register successfully sent MDN in DB tracking.


##Upgrade Notes
 See the openAS2HowTo appendix for the general process on upgrading OpenAS2.
 Below are some specific things to focus on depending on which version you are upgrading from.

 **You must review all notes for the relevant intermediate versions from your version to this release version.**

### If upgrading from versions older than 2.12.0:
      1. If you are using the DB tracking module with the default H2 database then you will need to follow the DB upgrade steps "Appendix: Updating database structure" defined in the OpenAS2HowTo.pdf to ensure you do not lose your existing data because the new H2 version has issues with old databases.


### If you have been passing the password for the certificate file on the command line in a shell script (no change to the Windows .bat file):
      1. The mechanism to pass the password on the command line has changes. You must now use this format:
          -./start_openas2.sh -Dorg.openas2.cert.Password=<keyStorePwd>
          where <keyStorePwd> is either a reference to an environment variable or  the actual paasword itself.
          Since passing the password in the command line does not provide for any additional security, it is recommended you use the new environment variable option described in the docs.

### If upgrading from versions older than 2.9.4:
      1. There is a script in the "upgrade" folder : <installDir>/bin/upgrade/config_transform.sh.
         This script can be run without parameters to get usage message.
         The simplest way to run it is to open a terminal in the upgrade folder and run:
            config_transform.sh <path to old config.xml file>
          This will produce a file named config.xml.new in the upgrade folder. Copy this file to the config.xml in the new OpenAS2 version.
          Windows user can use the new Linux shell to run the above script or run this command from within the upgrade folder:
         java -jar lib/saxon9he.jar -xsl:config.xslt  -o:config.xml.new -s:<path to old config.xml file>

### If upgrading from versions older than 2.9.0:
      1. Run the schema upgrade process as defined in the Appendix for the relevant database you are tracking messages in.

### If upgrading from versions older than 2.5.0:
      1. Change the name of the MDN sender module from "AsynchMDNSenderModule" to "MDNSenderModule" in the config.xml if using your existing config.xml file in the upgrade. If "AsynchMDNSenderModule" is not in the config then add the following: <module classname="org.openas2.processor.sender.MDNSenderModule" retries="3"/>
      2. Change the name of the partnership attribute "messageid" to "as2_message_id_format" if used in any partnership definition.
      3. Change the "as2_mdn_options" attribute to use $attribute.sign$ instead of hard coded signing algorithm
      4. If you experience issues with partners failing that were working in the previous version, check the troubleshooting section of the OpenAS2HowTo guide - specifically the issues around Content Transfer Encoding and Content Length/Chunking


### If upgrading from versions older than 2.4.1:
      1. If you have developed custom modules to enhance OpenAS2 they will need to be upgraded to include a healthcheck() method.
### If upgrading from versions older than 2.3.0:
      1. If using a custom startup script, re-integrate your customizations into the new script as the jar file for OpenAS2 is now tagged with its release version. (use asterisk [*] in classpath)
### If upgrading from versions older than 2.1.0:
      1. Add the new module to your existing config.xml (see classname="org.openas2.processor.msgtracking.DbTrackingModule" in release config.xml)
      2. If using a custom startup script, re-integrate your customizations into the new script
      3. As of 2.3.1 the log date format was changed to international standard. If you have log processing utilities that rely on a specific date format then you must change as needed (see documentation for mechanism)
  
Java 1.7 or later is required.

## NOTE FOR JAVA 1.5: No longer supported. Use a version prior to 2.2.0 to run on Java 1.5

## NOTE FOR JAVA 1.6: No longer supported as of version 2.5.0. For older versions of OpenAS2 see below
	The version of H2 database included in this release used for storing tracking messages will only support Java 1.7.
	If you do not need the DB tracking feature then simply remove it from the config.xml file.
	Otherwise:
		- download the older version of H2 that was compiled with support for Java 1.6 from this site:
			https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/h2database/h2-2014-01-18.zip
		- Extract the file named h2-1.3.175.jar from the zip and replace the h2-1.4.192.jar in the "lib" folder with this one.
		- Change the startup script to include the replaced version of H2 jar in the classpath
		- Start OpenAS2 (required to run next statement successfully) and import the DDL (located in the config folder as db_ddl.sql) into the DB created by the older version like this:
			java -cp [path to OpenAS2 install]/lib/h2-1.3.175.jar org.h2.tools.RunScript -user sa -password OpenAS2 -url jdbc:h2:tcp://localhost:9092/openas2 -script [path to OpenAS2 install]/config/db_ddl.sql

## Historical list of changes: see the changes.txt file in the release package
