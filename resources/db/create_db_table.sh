#!/bin/sh
#ant -Djdbc.properties.file=jdbc.properties.postgresql writeSchemaToDb
ant -Djdbc.properties.file=jdbc.properties.h2 writeSchemaToDb
