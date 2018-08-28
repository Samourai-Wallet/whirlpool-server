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
server.rpc-client.protocol = http
server.rpc-client.host = CONFIGURE-ME
server.rpc-client.port = CONFIGURE-ME
server.rpc-client.user = CONFIGURE-ME
server.rpc-client.password = CONFIGURE-ME
```
The bitcoin node should be running on the same network (main or test).<br/>
The node will be used to verify UTXO and broadcast tx.

### UTXO amounts
```
server.mix.denomination: amount in satoshis
server.mix.miner-fee-min: minimum miner-fee to pay by mustMix
server.mix.miner-fee-max: maximum miner-fee to pay
```
UTXO should be founded with:<br/>
for mustMix: (*server.mix.denomination* + *server.mix.miner-fee-min*) to (*server.mix.denomination* + *server.mix.miner-fee-max*)<br/>
for liquidities: (*server.mix.denomination*) to (*server.mix.denomination* + *server.mix.miner-fee-max*)

### UTXO confirmations
```
server.register-input.min-confirmations-must-mix: minimum confirmations for mustMix inputs
server.register-input.min-confirmations-liquidity: minimum confirmations for liquidity inputs
```

### UTXO rules
```
server.register-input.max-inputs-same-hash: max inputs with same hash (same origin tx) allowed to register to a mix
```

### Mix limits
```
server.mix.anonymity-set-target = 10
server.mix.anonymity-set-min = 6
server.mix.anonymity-set-max = 20
server.mix.anonymity-set-adjust-timeout = 120

server.mix.must-mix-min = 1
server.mix.liquidity-timeout = 60
```
Mix will start when *server.mix.anonymity-set-target* (mustMix + liquidities) are registered.<br/>
If this target is not met after *server.mix.anonymity-set-adjust-timeout*, it will be gradually decreased to *server.mix.anonymity-set-min*.<br/>

At the beginning of the mix, only mustMix can register. Meanwhile, liquidities connecting are placed on a waiting pool.<br/>
After *server.mix.liquidity-timeout* or when current *anonymity-set-target* is reached, liquidities are added as soon as *server.mix.must-mix-min* is reached, up to *server.mix.anonymity-set-max* inputs for the mix.

### Exports
Each mix success/fail is appended to a CSV file:
```
server.export.mixs.directory
server.export.mixs.filename
```

### Testing
```
server.rpc-client.mock-tx-broadcast = false
```
For testing purpose, *server.rpc-client.mock-tx-broadcast* can be enabled to mock txs instead of broadcasting it.
When enabled, server will keep whirlpool txs in memory until server restart and act as if these txs are confirmed in blockchain.

### Building
- Use *build.sh* on your local developer machine:
```
./build.sh
```

This script:
 * clones whirlpool modules from git remote
 * mvn install each module
 * generate whirlpool-server-*.jar to *./build* directory

