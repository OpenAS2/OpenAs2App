# OpenAS2 MQ Plugin

## Overview
This plugin allows OpenAS2 to interconnect with a Message Queue for message exchange
and message tracking events
## Installation
The plugin is enabled by adding the included Jars in the lib folder of your OpenAS2
installation and appending the module configuration section in the config.xml file
## Mode of Operation
The Module operates by creating 2 Outbound Topic publishing destinations and one 
input consuming queue:

 - Messages published into the input consuming queue will get wrapped into an AS2 message 
and sent out to the corresponding partner based on the Partnership configuration file

 - AS2 Messages received including MDNs will be published in the Messages Output 
publishing Topic

 - Any tracking events from the messages generated from either the AS2 Messages sent/received
will be posted as Event messages in the Event Output Publishing Topic

### Adapters
The module includes 2 adapters to connect with MQ Brokers:
 - A generic JMS implementation of the MQ Broker connection
 - A RabbitMQ implementation using the Java Native RabbitMQ client library

To use either, the corresponding Jar for the Driver library used on the adapter
must be also added to the server's Lib folder.

Authentication parameters and other details needed by the driver should be added
as attributes on the module declaration on config.xml

#### JMS Adapter
The JMS adapter uses the following parameters:
  - jms_factory_jndi : The JNDI path of the Connection Factory used for JMS Connection
#### RMQ Adapter
  - uri: AMQP Connection string to RabbitMQ, including Username/password, Host and Port
  - virtualhost: Virtual host to attach
