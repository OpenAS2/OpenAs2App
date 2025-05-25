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
Follow the instructions in the WebUI/README.md file for configuring and using it.


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
$ docker run -it --rm -p 4080:10080 -p 4081:10081 -p 8443:8080 -v ${PWD}/config:/opt/openas2/config -v ${PWD}/data:/opt/openas2/data openas2:latest
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
NOTE: Some users have reported that using --net=host does not work for them and removing it solves the problem..

```console
$ docker run -it --rm --net=host -p 4080:10080 -p 4081:10081 -p 8443:8080 -v ${PWD}/config:/opt/openas2/config -v ${PWD}/data:/opt/openas2/data openas2:latest
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

If the docker images are not locally installed you have to specify the full path at the Server field. (ex. http://192.168.1.100:8443/api) and either use a reverse proxy or allow connections from any location (config.xml restapi.command.processor.baseuri="http://0.0.0.0:8080").

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

## Dynamically configure your container using environment variables

Here is a short explaination how to override properties in the container's `config.xml` file using environment variables. 

**Prerequisites:**

* The container environment needs to have environment variables starting with the prefix `OPENAS2PROP_`.

**Process:**

1. **Start the container**: Start the container using your preferred method (e.g., Docker run command).
2. **Environment variables take precedence**: The script running within the container (assumed to be `start-container.sh`) checks for the existence of `config.xml` in the `$OPENAS2_BASE/config` directory. 
3. **Missing `config.xml`**: If `config.xml` is missing, the script:
   - Copies the contents of the `config_template` directory into the `config` directory.
4. **Missing `OPENAS2_PROPERTIES_FILE`**: If the `OPENAS2_PROPERTIES_FILE` is not found:
   - The script logs a warning message using the defined colorized `echo_warn` function.
   - It processes environment variables to generate properties file content.
     - Environment variables starting with the prefix `OPENAS2PROP_` are considered.
     - For each environment variable:
       - The script removes the prefix using string manipulation.
       - It replaces double underscores with dots (`__` to `.`) in the variable name.
       - The name is converted to lowercase.
       - The value of the environment variable is retrieved.
       - The script logs the processed name and value with color using the `echo_ok` function.
       - Finally, it writes the processed name and value in the format `"name=value"` to the `OPENAS2_PROPERTIES_FILE`.
5. **Start OpenAS2**: After processing (if any), the script continues by calling the `start-openas2.sh` script located in the same directory to launch the OpenAS2 server.

**Notes:**

* This script provides a way to . 
* Ensure the environment variables have appropriate access permissions and values within your container environment.
* The script uses color-coded output (if the terminal supports it) to differentiate between warnings and successful operations. 
* You can customize the color definitions or remove the colorization logic if not needed.
* Remember to adjust the script path and variable names based on your specific container setup.
