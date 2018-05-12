# whirlpool-server
Whirlpool server

## Usage
- create local server configuration:
```
cd whirlpool-server
cp src/main/resources/application.properties ./custom.properties
```
Settings from custom.properties will override default settings (src/main/resources/application.properties)

- run from commandline:
```
mvn clean install -Dmaven.test.skip=true
java -jar target/whirlpool-server-0.0.1-SNAPSHOT.jar --spring.config.location=./custom.properties [--debug]
```
