#!/bin/sh

if [ ! -e /opt/openas2/config/config.xml ]
    then
        echo "The config folder is empty, it will be populated by the template..."
        cp -a config_template/* config/
        echo "Done!"
fi
$(dirname $0)/start-openas2.sh
