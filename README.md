# whirlpool-server
Whirlpool server

## Usage
- create local server configuration:
```
cd whirlpool-server
cp src/main/resources/application.properties ./custom.properties
```
custom.properties will override default settings (src/main/resources/application.properties)

The following settings *NEED* to be overriden:
```
server.rpc-client.host = CONFIGURE-ME
server.rpc-client.user = CONFIGURE-ME
server.rpc-client.password = CONFIGURE-ME
```

- run from commandline:
```
mvn clean install -Dmaven.test.skip=true
java -jar target/whirlpool-server-0.0.1-SNAPSHOT.jar --spring.config.location=./custom.properties [--debug]
```
