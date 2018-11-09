# whirlpool-server install
Whirlpool server installation procedure

## Fresh install
- create local server configuration to override default settings:
```
cd whirlpool-server
cp src/main/resources/application.properties ./custom.properties
```

- build and run:
```
mvn clean install -Dmaven.test.skip=true
java -jar target/whirlpool-server-version.jar --spring.config.location=./custom.properties [--debug]
```

- create the file ${server.export.directory}/mixs.csv
```
touch /myexportdir/mixs.csv
```

