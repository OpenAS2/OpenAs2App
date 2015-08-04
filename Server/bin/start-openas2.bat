rem Purpose:  runs the OpenAS2 application
set JAVA_EXE=%java_home%/bin/java 
rem    
rem remove -Dorg.apache.commons.logging.Log=org.openas2.logging.Log if using another logging package
rem
"%JAVA_EXE%" -Xms32m -Xmx384m -Dorg.apache.commons.logging.Log=org.openas2.logging.Log  -cp .:../lib/activation.jar:../lib/mail.jar:../lib/bcprov-jdk14-125.jar:../lib/bcmail-jdk14-125.jar"../lib/commons-logging-1.1.1.jar:../lib/OpenAS2_20100816.jar org.openas2.app.OpenAS2Server ../config/config.xml
