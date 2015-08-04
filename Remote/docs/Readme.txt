The OpenAS2Server must be running.  Its config.xml file must have 
a reference to the SocketCommandProcessor module.

OpenAS2Servlet parameters in web.xml:
	   loggingPort  - port that SocketLogging function is listening to.  
	     see <logger classname="org.openas2.logging.SocketLogger"> 
	     in config.xml of OpenAS2Server.
	   commandHostID - host id that SocketLogging function (OpenAS2Server) 
	     is running on.
	   commandPort - port that SocketCommandProcessor is listening to.
	      see <commandProcessor classname="org.openas2.cmd.processor.SocketCommandProcessor"> 
	      in config.xml of OpenAS2Server.
	   commandUserID  - userid validated by login process and used by SocketCommandProcessor.
	      see <commandProcessor classname="org.openas2.cmd.processor.SocketCommandProcessor"> 
	      in config.xml of OpenAS2Server.
       commandPWD - password  validated by login process and used by SocketCommandProcessor.
	      see <commandProcessor classname="org.openas2.cmd.processor.SocketCommandProcessor"> 
	      in config.xml of OpenAS2Server.

To run CommandLine use the following from your OS prompt (assumes the 
class file is in the classpath)
  java org.openas2.remote.CommandLine hostname portnumber userid password
     where hostname is the machine the OpenAS2Server is running on.
           portnumber is the port number the SocketCommandProcessors is 
             listening on. see <commandProcessor 
             classname="org.openas2.cmd.processor.SocketCommandProcessor"> 
		     in config.xml of OpenAS2Server.
           userid is the user associated to SockentCommandProcessor
             as defined in the config.xml file. see <commandProcessor 
             classname="org.openas2.cmd.processor.SocketCommandProcessor"> 
		     in config.xml of OpenAS2Server.
           password is the user password associated to 
             SockentCommandProcessor see <commandProcessor 
             classname="org.openas2.cmd.processor.SocketCommandProcessor"> 
                