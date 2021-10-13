# ergo-subpooling
ergo-subpooling is a smart contract based mining pool that allows groups of friends to pool together their hashrates in order to get mining pool rewards quicker than they would alone. The goal of this dApp is to help encourage decentralization of mining pools while also promoting small time miners who may not be able to get block rewards fast enough on normal mining pools. This project was developed for ERGOHACK II.

## How to run
To run, simply download the jar and run 
```java -jar ergo-subpooling-0.1.jar``` 
in command line or terminal.
### [Download Here](https://github.com/K-Singh/ergo-subpooling/raw/master/ergo-subpooling-0.1.jar)
If it says you need a subpool_config.json file, look at the one given [here](https://github.com/K-Singh/ergo-subpooling/blob/309a5e7d957a5455a8856d4ef251ab80c757b1d9/subpool_config.json) in the repository as an example.

Alternatively, you may clone the repository and run: 
```
cd ergo-subpooling
sbt assembly
```
In this way you can get around running a precompiled jar.


## Current Supported Mining Pools
https://enigmapool.com

ergo-subpooling only supports one mining pool at the moment. The goal is to incorporate as many mining pools as possible, especially smaller ones so as to increase security
of the Ergo blockchain. If you own a mining pool and would like your pool to be supported, please message me or send a pull request.

### This project was made using Ergo-Appkit
You can find it here: https://github.com/ergoplatform/ergo-appkit

### This project was inspired by ErgoSmartPools
https://github.com/WilfordGrimley/ErgoSmartPools
