![logo](https://raw.githubusercontent.com/igwtech/OpenAs2App/master/docs/as2_logo.png)

# As2Server
The OpenAS2 application enables you to transmit and receive AS2 messages with EDI-X12, EDIFACT, XML, or binary payloads between trading partners.


## Development
There is a pom.xml in the Server folder to compile and create the jar and build the distribution package using Maven.
The current version of the application is extracted from the POM and inserted into the MANIFEST.MF at build time.
More detailed information is available in the DeveloperGuide.odt in the docs folder in Github.

## Test, Build and Package
The following commands can be used in the build process.

Checking dependency tree:
`./mvnw dependency:tree`

Checking dependencies against latest:
`./mvnw versions:display-dependency-updates`

Updating dependencies to latest:
`./mvnw versions:use-latest-releases`

Build a snapshot the following command should be used:
`./mvnw versions:set -DnewVersion=2.12.0-SNAPSHOT`

Run unit tests:
`./mvnw test`

Build a package
`./mvnw clean package`


## Deploy to Maven Central
To deploy the released artifacts requires user ID and password for Sonatype. See developer guide for details:
`./mvnw clean deploy -P release` - will require manual closing and release in Sonatype
`./mvnw release:perform`
`./mvnw nexus-staging:release -Ddescription="Some release comment here"`

## Web UI for configuration
IMPORTANT: The WebUI will NOT work with Java 8 - you need Java 11 or newer
Follow the instructions in the WebUI/README.md file for confoguring and using it.


## How to create the docker image

To create the docker image, use the Dockerfile in the project.
In the terminal, open the folder where the Dockerfile is located.
Use commands below to login to your dockerhub account through terminal.

Run below command to create image with name and tag.

```console
$ docker build -t openas2:latest .
```

## How to use this image.

Run the default OpenAS2 server:

```console
$ docker run -it --rm openas2:latest
```

You can test it by visiting `http://container-ip:10080` in a browser or, if you need access outside the host, on port 4080:

```console
$ docker run -it --rm -p 4080:10080 -p 4081:10081 -p 8443:8443 openas2:latest
```

You can then go to `http://localhost:4080` or `http://host-ip:4080` in a browser (noting that it will return a 401 since there are no proper AS2 headers sent by the browser by default).

The default OpenAS2 environment in the image is:

	OPENAS2_BASE:    /usr/local/tomcat
	OPENAS2_HOME:    /usr/local/tomcat
	OPENAS2_TMPDIR:  /usr/local/tomcat/temp
	JAVA_HOME:       /usr/local/openjdk-11

The configuration files are available in `/opt/openas2/config/`.

## How to use the WebUI docker image

Build the server image:

```console
$ docker build -t openas2:latest .
```

Run the OpenAS2 server, with its network set to "host", so that the WebUI can access the server.

```console
$ docker run -it --rm --net=host -p 4080:10080 -p 4081:10081 -p 8443:8443 openas2:latest
```

In a separate terminal, build the WebUI docker image:

```console
$ docker build -t openas2_webui:latest -f Dockerfile_WebUI .
```

Run the WebUI docker image, using port 8080 on the host:

```console
$ docker run --rm -p 8080:80 openas2_webui:latest
```

Visit http://localhost:8080 and login with "userID" and "pWd".
Note: You may have to login twice if you get a "Network Error" the first time.

## Docker Compose

Build the images:

```console
$ docker compose build
```

Run the images:

```console
$ docker compose up
```

Run the images in the background:

```console
$ docker compose up -d
```

View specific container logs:

```console
$ docker compose logs openas2
$ docker compose logs openas2_webui
```
