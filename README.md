# ergo-subpooling
ergo-subpooling is a smart contract based mining pool that allows groups of friends to pool together their hashrates in order to get mining pool rewards quicker than they would alone. The goal of this dApp is to help encourage decentralization of mining pools while also promoting small time miners who may not be able to get block rewards fast enough on normal mining pools. This project was developed for ERGOHACK II.

# How it works
Subpooling allows for groups of friends to mine to the same address on a mining pool(Currently either Enigma Pool or HeroMiners). Any member of the subpool may enter the
```create``` command and input all of the required information to make the subpool. At the end, they will get some text that they can paste into the ```parameters``` field of their ```subpool_config.json```. To ensure that everyone is mining to the correct subpool, each member of the subpool will copy and paste this same text into their own parameters field.
The only field that members will have to change will be the ```workerName``` field.

## The Holding Phase
Once enough mining has been done, the mining pool will send a payout to the subpool. At this point, each member of the mining pool may send a withdraw request to the
smart contract protecting their funds. Each withdraw request contains information about the subpool's state. This information consists of the number of share's submitted
by each subpool member, along with the total share's submitted by the entire pool. When all members of the subpool have sent a withdraw request, the next phase begins.

## The Consensus Phase
At this point, all member's have submitted their withdraw requests to the smart contract. Now, any member of the subpool may enter the 'distribute' command into their
subpooling program. At this point, the smart contract will take the average of each subpool state sent by each member and reach a consensus about the subpool's state.
This consensus will then be used to figure out how much ERG each member of the subpool gets from the total payout. Members of the subpool are paid according to the proportion
of shares they submitted to the subpool.

## An Example
Alice and Bob are both mining to Herominers and decide to make a subpool. They go through the program instructions and then mine to the subpool for a few days until they get their payout of 0.5 ERG. Alice decides to send a withdraw request first, she enters the withdraw command which pings Herominers for information about the subpool. Herominers then sends the information back to Alice's subpooling program. This information is then sent to the smart contract protecting Alice and Bob's funds. The information consists of 3 things: Alice's share number, Bob's share number, and the total Share number. 

Lets say during Alice's withdraw request, the information she got was:

```[50, 25, 75]```

A day later Bob sends his withdraw request, which has this information from the mining pool: 

```[100, 50, 150]```

Now that both Bob and Alice have sent withdraw requests, either Bob or Alice can enter the distribute command. Bob sends this command to the smart contract which takes the average from both withdraw requests and comes to a consensus about how many shares Alice or Bob sent. In this case the consensus will look like this:

```[75, 37.5, 112.5]```

From this consensus, the smart contract will determine how much ERG each member of the subpool will get. In this case, Alice will get:

```(75 / 112.5) * (0.5 - (MinTxFee / 2)) = ~0.32```

While Bob will get:

```(37.5 / 112.5) * (0.5 - (MinTxFee / 2)) = ~0.16```

The transaction fee is split up evenly between each member of the subpool.

# How to run
To run, simply download the jar and run 
```java -jar ergo-subpooling-0.2.jar``` 
in command line or terminal.
### [Download Here](https://github.com/K-Singh/ergo-subpooling/raw/master/ergo-subpooling-0.2.jar)
If it says you need a subpool_config.json file, look at the one given [here](https://github.com/K-Singh/ergo-subpooling/blob/309a5e7d957a5455a8856d4ef251ab80c757b1d9/subpool_config.json) in the repository as an example.

Alternatively, you may clone the repository and run: 
```
cd ergo-subpooling
sbt assembly
```
In this way you can get around running a precompiled jar.

## Instructions
Once the program is running, you may either create a subpool, or load one from the config. To create a subpool, simply type ```create``` into the console.
The program will ask for the payment address of each subpool member. Once these have been inputted type ```done``` and then input the worker names that each miner will use.
Worker names should be entered in the same order as the addresses. The program will then ask for your worker name again. This is to ensure that each member of the subpool has some unique address that corresponds to their unique worker name. Finally, the mining pool's payout amount must be inputted. 

By following these steps, the program will print out some JSON text that you can then paste into your ```subpool_config.json```. This text should be pasted into the
```parameters``` field of your config file. Each member of the subpool will paste this same exact text into their config file with one change. The field ```workerName```
should be different in each subpool member's config file and will correspond to that member's worker name. 

## Example Of Parameter Fields Of Two Members Of The Same Subpool:

### Member One:
```
"parameters":{
  "workerName": "MemberOne_Worker",
  "minerAddressList": [
    "9hbWV5nJtHwwU8Z6EVou9vh6VoDyQLTqTTnsUTCHHD7eRNTBDMA",
    "9gFLSKKoz9ZXwzBW51xjAStnFc7zFXYHuGxiyRt9kzgLXeRa2po"
  ],
  "workerList": [
    "MemberOne_Worker",
    "MemberTwo_Worker"
  ],
  "holdingAddress": "TkfL4THLVGRDihCwCZFho6KRfJKasEbdCUoGjaZfgd7UKdaoVByxNHYxb7J5tjdiGEFF4nAcJpgNKmduQE1WsGQmSTm3ixrqsSFKYV6c75TRSWixSKkyFgHqaXCsecQuAjaCq7DnRtHRMMtmgybL2mSJimmxPGx7hgRLTkRi4AheKGDFEffA97suhDsrCNj5JB43BkHFvMQEVoUAAh8wMB5gNCQQ1w4iZenuynt6DuqXHjbSteRc8qongATsratfKBwYALYqTQbxfJEbirxwrQimzbwRzsJRyw7PkUy8QNNqV4VnKxeQJ9fE5seTDyAT4Z7JHFVCdaSct7mBSdDfw7Escv9PXpCX3s8jWDZHw",
  "consensusAddress": "3znqbr9Dqcfbf2pjYxevdhjR38bcSDtozg2Fgmytz5P8RwGTDM5ge8BhZWbm3Em6bQyYfXRC4YLrVGyQaea8MPiusLk4xFAyub62fU9RLhVGkUJXrEXpPZPdBuHV2gZGSZUjSMi98tdUiGw2nZBppVNeQJHwPfFw76txJSGcHmcMq4rhKhYg5H63BFMkb1FnjAMNLfExnNez4DUfhgwChQbbnERRS9t5ij6kX8BeKn5usHSEQWq56zZB61SxKBArajpn6wL5h2v3BhaY2JYXiPJU2x1LFjNoF2rUaXGpAhQhLCaQJ9g7fGA5WEepU4GtEuqcUT7BaWYquqqBidA3dWZLR5TaqCDv9TS7tVcJAe3dzBYM8C9vvpvm7D484yzJyvH3wrM5eJvLnU7pHWn4fQQex7hsqGFyTkiX731tvWGH3yw1a86DTejSqB8cY3zUgQULpA96ofBFTY6zXorui1FLid3DxRkmX8jfYZ5K1HQkXXkHbQ3mjVgoJfGVVGFjrfCjK1bHzDQCDwNBbamr2FVg6UE29LSq6X6TrM",
  "minimumPayout": 0.5
  } 
  ```
  ### Member Two:
  ```
  "parameters":{
  "workerName": "MemberTwo_Worker",
  "minerAddressList": [
    "9hbWV5nJtHwwU8Z6EVou9vh6VoDyQLTqTTnsUTCHHD7eRNTBDMA",
    "9gFLSKKoz9ZXwzBW51xjAStnFc7zFXYHuGxiyRt9kzgLXeRa2po"
  ],
  "workerList": [
    "MemberOne_Worker",
    "MemberTwo_Worker"
  ],
  "holdingAddress": "TkfL4THLVGRDihCwCZFho6KRfJKasEbdCUoGjaZfgd7UKdaoVByxNHYxb7J5tjdiGEFF4nAcJpgNKmduQE1WsGQmSTm3ixrqsSFKYV6c75TRSWixSKkyFgHqaXCsecQuAjaCq7DnRtHRMMtmgybL2mSJimmxPGx7hgRLTkRi4AheKGDFEffA97suhDsrCNj5JB43BkHFvMQEVoUAAh8wMB5gNCQQ1w4iZenuynt6DuqXHjbSteRc8qongATsratfKBwYALYqTQbxfJEbirxwrQimzbwRzsJRyw7PkUy8QNNqV4VnKxeQJ9fE5seTDyAT4Z7JHFVCdaSct7mBSdDfw7Escv9PXpCX3s8jWDZHw",
  "consensusAddress": "3znqbr9Dqcfbf2pjYxevdhjR38bcSDtozg2Fgmytz5P8RwGTDM5ge8BhZWbm3Em6bQyYfXRC4YLrVGyQaea8MPiusLk4xFAyub62fU9RLhVGkUJXrEXpPZPdBuHV2gZGSZUjSMi98tdUiGw2nZBppVNeQJHwPfFw76txJSGcHmcMq4rhKhYg5H63BFMkb1FnjAMNLfExnNez4DUfhgwChQbbnERRS9t5ij6kX8BeKn5usHSEQWq56zZB61SxKBArajpn6wL5h2v3BhaY2JYXiPJU2x1LFjNoF2rUaXGpAhQhLCaQJ9g7fGA5WEepU4GtEuqcUT7BaWYquqqBidA3dWZLR5TaqCDv9TS7tVcJAe3dzBYM8C9vvpvm7D484yzJyvH3wrM5eJvLnU7pHWn4fQQex7hsqGFyTkiX731tvWGH3yw1a86DTejSqB8cY3zUgQULpA96ofBFTY6zXorui1FLid3DxRkmX8jfYZ5K1HQkXXkHbQ3mjVgoJfGVVGFjrfCjK1bHzDQCDwNBbamr2FVg6UE29LSq6X6TrM",
  "minimumPayout": 0.5
  } 
  ```
  ## Final Steps
After this, members should ensure that the secret mneumonic phrase corresponding to their address in the subpool is inputted in the wallets section of their ```subpool_config.json```. When this phrase and the wallet spending password have been inputted, the subpool member will be eligible to send withdrawal and distribution requests to the smart contracts protecting their funds. The only thing left to do after this is to enter the ```holdingAddress``` into their mining software. For example, if Member One
used NBMiner to mine, they would simply enter this into their NBMiner's ```start_ergo.bat```:

```
nbminer -a ergo -o stratum+tcp://ca.ergo.herominers.com:1180 -u TkfL4THLVGRDihCwCZFho6KRfJKasEbdCUoGjaZfgd7UKdaoVByxNHYxb7J5tjdiGEFF4nAcJpgNKmduQE1WsGQmSTm3ixrqsSFKYV6c75TRSWixSKkyFgHqaXCsecQuAjaCq7DnRtHRMMtmgybL2mSJimmxPGx7hgRLTkRi4AheKGDFEffA97suhDsrCNj5JB43BkHFvMQEVoUAAh8wMB5gNCQQ1w4iZenuynt6DuqXHjbSteRc8qongATsratfKBwYALYqTQbxfJEbirxwrQimzbwRzsJRyw7PkUy8QNNqV4VnKxeQJ9fE5seTDyAT4Z7JHFVCdaSct7mBSdDfw7Escv9PXpCX3s8jWDZHw.MemberOne_Worker
pause
```

Once this has been done, the subpool is completely set up and each member should have begun mining to it.
  

# Current Supported Mining Pools
https://enigmapool.com

https://ergo.herominers.com/

ergo-subpooling only supports two mining pools at the moment. The goal is to incorporate as many mining pools as possible, especially smaller ones so as to increase security
of the Ergo blockchain. If you own a mining pool and would like your pool to be supported, please message me or send a pull request.

## This project was made using Ergo-Appkit
You can find it here: https://github.com/ergoplatform/ergo-appkit

## This project was inspired by ErgoSmartPools
https://github.com/WilfordGrimley/ErgoSmartPools
