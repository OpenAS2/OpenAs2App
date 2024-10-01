#!/bin/sh
export OPENAS2_PROPERTIES_FILE=${OPENAS2_PROPERTIES_FILE:-$OPENAS2_BASE/config/openas2.properties}
if [ $(tput colors) -ge 8 ]; then
    RED=$(tput setaf 1)
    GREEN=$(tput setaf 2)
    NORMAL=$(tput sgr0)
fi
echo_warn() {
    echo "${RED}${1}${NORMAL}"
}
echo_ok() {
    echo "${GREEN}${1}${NORMAL}"
}
if [ ! -e $OPENAS2_BASE/config/config.xml ]
    then
        echo_warn "The config folder is empty, it will be populated by the template..."
        cp -a $OPENAS2_BASE/config_template/* $OPENAS2_BASE/config/                
        echo_ok "Done!"
fi
if [ ! -e $OPENAS2_PROPERTIES_FILE ]
    then
    echo_warn "Missing properties file" 
    echo_ok "Processing Environment Variables into properties"    
    # Define the prefix for matching environment variables
    prefix="OPENAS2PROP_"

    # Process each environment variable starting with the prefix
    for env_var in $( env | grep "^${prefix}" | cut -d'=' -f1 )
    do
    if [ -z "$env_var" ]; then
        continue
    fi
    
    # Remove the prefix
    modified_name="${env_var#${prefix}}"

    # Replace double underscores with dots
    modified_name=$( echo "${modified_name}" | sed 's/__/./g' )
    
  
    # Convert to lowercase
    modified_name=$( echo "$modified_name" | tr '[:upper:]' '[:lower:]' )

    # Extract the value of the environment variable
    value=$( eval "echo \"\$$env_var\"" )
    echo "${GREEN}$modified_name=${NORMAL}${value}"
    # Construct the properties command-line argument
    echo "${modified_name}=${value}" >> $OPENAS2_PROPERTIES_FILE
    done

fi
# Start OpenAS2 in foreground
$(dirname $0)/start-openas2.sh
