# As2Server
The OpenAS2 application enables you to transmit and receive AS2 messages with EDI-X12, EDIFACT, XML, or binary payloads between trading partners.

# Development
There is a build.xml in the Server folder to compile and create the jar and build the distribution package
The current version is stored in org.openas2.Session.java - this must be updated whenever a new release is set up. The build file will automatically use the value entered there.

## Build

Maven is used as a build. Therefore in order to build a snapshot the following command should be used:

`./mvnw clean package`


`./mvnw versions:set -DnewVersion=2.3.0-SNAPSHOT`