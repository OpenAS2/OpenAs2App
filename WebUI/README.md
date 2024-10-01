# OpenAS2 WebUI
This is a simple implementation of Web-based user interface that interacts with the OpenAS2 Rest API. Its has been created in  HTML5, CSS & VueJS.  You will need Node.js (https://nodejs.org/en/) and Yarn (https://yarnpkg.com/lang/en/) to build the project, then just copy the files over to be served by any WebServer (Nginx, Apache, Tomcat, Wildfly, etc).


*********************************************************************
__IMPORTANT__
================

__THIS PACKAGE IS NOT ACTIVELY MAINTAINED OR SUPPORTED.__
__IT IS DEPRECATED AND WILL BE REMOVED FROM THIS REPOSITORY IN THE NEAR FUTURE.___

It only works on Java 11 and above.

*********************************************************************

## Features:
  - Authentication using Basic username/password against the REST API
  - List, view, delete and Upload new partner's public keys
  - List, view, delete and Create new partner's profile
  - List, view, delete and create new partnerships (connections) between two partners

## Project setup
```
yarn install
```

### Environment Variables
The OpenAS2 public REST api Endpoint needs to be configured with the VUE_APP_RESTAPI_URL env variable
```
VUE_APP_RESTAPI_URL=https://openas2/rest/api
```
### OpenAS2 Configuration
  1. Enable the REST command processor config/config.xml:
		restapi.command.processor.enabled="true"
  1. Set the listening port for the command procesor to something usable on your server if the feault is not usable in config/config.xml:
		restapi.command.processor.baseuri="http://0.0.0.0:8080"

### Compiles and hot-reloads for development
```
yarn run serve
```

### Compiles and minifies for production
```
yarn run build
```

### Run your tests
```
yarn run test
```

### Lints and fixes files
```
yarn run lint
```

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).
