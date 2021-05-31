![logo](https://raw.githubusercontent.com/igwtech/OpenAs2App/master/docs/as2_logo.png)

# As2Server
The OpenAS2 application enables you to transmit and receive AS2 messages with EDI-X12, EDIFACT, XML, or binary payloads between trading partners.


# Development
There is a pom.xml in the Server folder to compile and create the jar and build the distribution package using Maven
The current version of the application is extracted from the POM and instered into the MANIFEST.MF at build time.
More detailed information is available in the DeveloperGuide.odt in the docs folder in Github

## Build

Maven is used as a build. Therefore in order to build a snapshot the following command should be used:

`./mvnw clean package`


`./mvnw versions:set -DnewVersion=2.12.0-SNAPSHOT`

## Deploy to Maven Central
To deploy the released artifacts requires user ID and password for Sonatype. See developer guide for details:
`./mvnw clean deploy -P release` - will require manual closing and release in Sonatype
`./mvnw release:perform`
`./mvnw nexus-staging:release -Ddescription="Some release comment here"`

# How to use this image.

Run the default OpenAS2 server:

```console
$ docker run -it --rm openas2:latest 
```

You can test it by visiting `http://container-ip:10080` in a browser or, if you need access outside the host, on port 4080:

```console
$ docker run -it --rm -p 10080:4080 -p 10081:4081 openas2:latest
```

You can then go to `http://localhost:4080` or `http://host-ip:4080` in a browser (noting that it will return a 401 since there are no proper AS2 headers sent by the browser by default).

The default OpenAS2 environment in the image is:

	OPENAS2_BASE:    /usr/local/tomcat
	OPENAS2_HOME:    /usr/local/tomcat
	OPENAS2_TMPDIR:  /usr/local/tomcat/temp
	JAVA_HOME:       /usr/local/openjdk-11

The configuration files are available in `/opt/openas2/config/`. 
