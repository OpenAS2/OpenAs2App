#              OpenAS2 Server
#              Version 3.8.0
#              RELEASE NOTES
-----
The OpenAS2 project is pleased to announce the release of OpenAS2 3.8.0

The release download file is: OpenAS2Server-3.8.0.zip

The zip file contains a PDF document (OpenAS2HowTo.pdf) providing information on installing and using the application.
## NOTE: Testing covers Java 8 to 17. The application should work for older versions down to Java 7 but they are not tested as part of the CI/CD pipeline.

Version 3.8.0 - 2023-11-07
This is an enhancement release:
       **IMPORTANT NOTE**: Please review upgrade notes below if you are upgrading

  1. Support for configurable dynamic Content-Type based on the file extension. See documentation section 7.5 "Setting Content Type"

##Upgrade Notes
 See the openAS2HowTo appendix for the general process on upgrading OpenAS2.

### Upgrading to 3.6 or newer from 3.5 (or older) version:
      1. Run the following command after the upgrade of the code base is complete and BEFORE you start the OpenAS2 server:
          - Open a shell (Command prompt or Powershell in Windows or a Terminal window in NIX systems)
          - Run this command (NOTE the backslash escape to prevent command line expansion of asterisk):
              > java -cp /path/to/openas2/lib/\* <version of DB before upgrade> <DB user> <DB password> <path to DB file excluding the .mv.db extension>
            The version of the DB before upgrade will be the same last 3 digits of the h2 jar file in your install BEFORE the upgrade.
            If upgrading from 3.5.0 then the number is 214.
            The user ID, password and location of the DB file will be in your config file or if overridden then in the custom properties file.
            As an example assuming a vanilla install of OpenAS2 in /opt/OpenAS2 folder (replace / with \ for Windows):
              > cd /opt/OpenAS2
              > java -cp lib/\* UpgradeH2Database 214 sa OpenAS2 config/DB/openas2
 
### Upgrading to 3.xx from 2.x (or older) version:
## NOTE: The old config will work without change but it is strongly recommended that you follow these steps to convert your existing configuration to the new format as it provides a cleaner and less complicated setup and the old config will eventually be discontinued
      1. Follow the instructions for specific versions between your current version and this version as defined below before executing the commands below to convert your existing config.xml and partnerships.xml files to use the enhanced poller configuration.
      2. Open a terminal window (command window in Windows)
      3. Change to the <install>/config directory of the new version.
      4. Copy the config.xml and partnerships.xml from your existing version to the new version if not already done in other steps.
      5. Run this command: java -cp ../lib/\* org.openas2.upgrades.MigratePollingModuleConfig config.xml partnerships.xml
      6. A backup will be created of the original file (with .00 extension|) that can be removed if the conversion is successful.

 Below are some specific things to focus on depending on which version you are upgrading from.

 **You must review all notes for the relevant intermediate versions from your version to this release version.**

### If upgrading from versions older than 2.12.0:
      1. If you are using the DB tracking module with the default H2 database then you will need to follow the DB upgrade steps "Appendix: Updating database structure" defined in the OpenAS2HowTo.pdf to ensure you do not lose your existing data because the new H2 version has issues with old databases.
      2. A change to the way the private key is looked up in the receiver handler means that if you have duplicated a certificate in the keystore, some partnerships may start to fail. This fix may fix other strange certificate issues when receiving messages. To fix partnership failures that occur after the upgrade, find the duplicates and remove them making sure the one you leave behind is the one with the correct private key. Alternatively, use the **use_new_certificate_lookup_mode** attribute at partnership level set to **false** and the old mechanism will be used but this is not advised as a long term solution as it will eventually be removed in a future version.


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
