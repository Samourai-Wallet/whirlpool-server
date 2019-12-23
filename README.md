[![Build Status](https://travis-ci.org/Samourai-Wallet/whirlpool-server.svg?branch=develop)](https://travis-ci.org/Samourai-Wallet/whirlpool-server)
[![](https://jitpack.io/v/Samourai-Wallet/whirlpool-server.svg)](https://jitpack.io/#Samourai-Wallet/whirlpool-server)

# whirlpool-server

Server for [Whirlpool](https://github.com/Samourai-Wallet/Whirlpool) by Samourai-Wallet.

## Installation
See [README-install.md](README-install.md)

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

### Pool: UTXO amounts
```
server.pools[x].denomination: amount in satoshis
server.pools[x].miner-fee-min: minimum miner-fee accepted for mustMix
server.pools[x].miner-fee-max: maximum miner-fee accepted for mustMix
server.pools[x].miner-fee-cap: "soft cap" miner-fee recommended for a new mustMix (should be <= miner-fee-max)
```
UTXO should be founded with:<br/>
for mustMix: (*server.mix.denomination* + *server.mix.miner-fee-min*) to (*server.mix.denomination* + *server.mix.miner-fee-max*)<br/>
for liquidities: (*server.mix.denomination*) to (*server.mix.denomination* + *server.mix.miner-fee-max*)


### Pool: TX0 fees
```
server.pools[x].fee-value: server fee (in satoshis) for each tx0
server.pools[x].fee-accept: alternate fee values accepted (key=fee in sats, value=maxTx0Time)
```
Standard fee configuration is through *fee-value*.
*fee-accept* is useful when changing *fee-value*, to still accept unspent tx0s <= maxTx0Time with previous fee-value.


### UTXO confirmations
```
server.register-input.min-confirmations-must-mix: minimum confirmations for mustMix inputs
server.register-input.min-confirmations-liquidity: minimum confirmations for liquidity inputs
server.register-input.liquidity-interval = 10: liquidities are added by batch at this frequency
```

### UTXO rules
```
server.register-input.max-inputs-same-hash: max inputs with same hash (same origin tx) allowed to register to a mix
server.register-input.max-inputs-same-user-hash: max inputs with same user-hash (same mixing client) allowed to register to a mix
```

### SCodes
```
server.samourai-fees.scodes[foo].payload = 12345
server.samourai-fees.scodes[foo].fee-value-percent = 50
server.samourai-fees.scodes[foo].expiration = 1569484078 # optional

server.samourai-fees.scodes[bar].payload = 23456
server.samourai-fees.scodes[bar].fee-value-percent = 0
```
SCodes are special codes usable to enable special rules for tx0.
Each SCode is mapped to a short value payload (-32,768 to 32,767) which will be embedded into tx0's OP_RETURN as WhirlpoolFeeData.feePayload.
Payload '0' is forbidden, which is mapped to WhirlpoolFeeData.feePayload=NULL.
SCode overrides standard fee-value with a percent of this value.
SCode can expire for tx0s confirmed after a specified time.

### Pool: Mix limits
```
server.pools[x].anonymity-set-target = 10
server.pools[x].anonymity-set-min = 6
server.pools[x].anonymity-set-max = 20
server.pools[x].anonymity-set-adjust-timeout = 120

server.pools[x].must-mix-min = 1
server.pools[x].liquidity-min = 1
```
Mix will start when *anonymity-set-target* (mustMix + liquidities) are registered.<br/>
If this target is not met after *anonymity-set-adjust-timeout*, it will be gradually decreased to *anonymity-set-min*.<br/>

At the beginning of the mix, only mustMix can register up, to *anonymity-set-max - liquidity-min*. Meanwhile, liquidities are placed on a waiting pool.<br/>
Liquidities are added as soon as *must-mix-min* is reached, up to *anonymity-set-max* inputs for the mix.

### Exports
Each mix success/fail is appended to a CSV file:
```
server.export.mixs.directory
server.export.mixs.filename
```

### Testing
```
server.rpc-client.mock-tx-broadcast = false
server.test-mode = false
```
For testing purpose, *server.rpc-client.mock-tx-broadcast* can be enabled to mock txs instead of broadcasting it.
When enabled, *server.test-mode* allows client to bypass tx0 checks.

java -jar -Dspring.profiles.active=test target/whirlpool-server-develop-SNAPSHOT.jar --debug --spring.config.location=classpath:application.properties,/path/to/application-default.properties

## Resources
 * [whirlpool](https://github.com/Samourai-Wallet/Whirlpool)
 * [whirlpool-protocol](https://github.com/Samourai-Wallet/whirlpool-protocol)
 * [whirlpool-client](https://github.com/Samourai-Wallet/whirlpool-client)
