# As2Server
The OpenAS2 application enables you to transmit and receive AS2 messages with EDI-X12, EDIFACT, XML, or binary payloads between trading partners.

# Development
There is a pom.xml in the Server folder to compile and create the jar and build the distribution package using Maven
The current version of the application is extracted from the POM and instered into the MANIFEST.MF at build time.
More detailed information is available in the DeveloperGuide.odt in the docs folder in Github

To test this version it is necessary to indicate an existing database.
    config.xml : <dbconfig name="as2_bd" url="jdbc:mysql://127.0.0.1/openas2?characterEncoding=UTF-8" user="openas2" password="openas2"/>
    The queries to create the tables: createDefaultTable.sql

## Build

Maven is used as a build. Therefore in order to build a snapshot the following command should be used:

`./mvnw clean package`


`./mvnw versions:set -DnewVersion=2.3.0-SNAPSHOT`
