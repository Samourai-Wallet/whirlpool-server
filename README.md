# whirlpool-server
Whirlpool server

## Usage
- create local server configuration to override default settings:
```
cd whirlpool-server
cp src/main/resources/application.properties ./custom.properties
```

- run from commandline:
```
mvn clean install -Dmaven.test.skip=true
java -jar target/whirlpool-server-0.0.1-SNAPSHOT.jar --spring.config.location=./custom.properties [--debug]
```



## Configuration
### RPC client
```
server.rpc-client.host = CONFIGURE-ME
server.rpc-client.user = CONFIGURE-ME
server.rpc-client.password = CONFIGURE-ME
```
The bitcoin node should be running on the same network (main or test).<br/>
The node will be used to verify UTXO and broadcast tx.

### UTXO amounts
```
server.round.denomination: amount in satoshis
server.round.miner-fee: miner fee (only paid by mustMix)
```
UTXO for mustMix should be founded with *server.round.denomination*<br/>
UTXO for liquidities should be founded with *server.round.denomination*+*server.round.miner-fee*

### Round
```
server.round.target-must-mix
server.round.min-must-mix
server.round.must-mix-adjust-timeout
```
Round will start after *server.round.target-must-mix* mustMix register.<br/>
If this target is not met after *server.round.must-mix-adjust-timeout*, it will be gradually decreased to *server.round.min-must-mix*.

### Liquidities
```
server.round.liquidity-ratio
```
If *server.round.liquidity-ratio=1*, server will use as many liquidities as mustMix.<br>
If *server.round.liquidity-ratio=0*, no liquidity will be used.<br>
