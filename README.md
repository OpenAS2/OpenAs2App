# As2Server
The OpenAS2 application enables you to transmit and receive AS2 messages with EDI-X12, EDIFACT, XML, or binary payloads between trading partners.

# Development
There is a pom.xml in the Server folder to compile and create the jar and build the distribution package using Maven
The current version of the application is extracted from the POM and instered into the MANIFEST.MF at build time.
More detailed information is available in the DeveloperGuide.odt in the docs folder in Github

## Build

Maven is used as a build. Therefore in order to build a snapshot the following command should be used:

`./mvnw clean package`


`./mvnw versions:set -DnewVersion=2.3.0-SNAPSHOT`

## Deploy to Maven Central
To deploy the released artifacts requires user ID and password for Sonatype. See developer guide for details:
`./mvnw clean deploy`
