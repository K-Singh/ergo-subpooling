# ergo-subpooling
ergo-subpooling is a smart contract based mining pool that allows groups of friends to pool together their hashrates in order to get mining pool rewards quicker than they would alone. Subpooling allows members to mine to the same address without worrying about one person controlling the funds. The goal of this dApp is to help encourage decentralization of mining pools while also promoting small time miners who may not be able to get block rewards fast enough on normal mining pools. This project was developed for ERGOHACK II.

# How It Works
Subpooling allows for groups of friends to mine to the same address on a mining pool(Currently either Enigma Pool or HeroMiners). Any member of the potential subpool may enter the ```create``` command and input all of the required information to make the subpool. At the end, they will be able to save their subpool to a config file. This config file may then be loaded using the ```load``` command to load the subpool into someone else's subpooling program. This person may then enter ```join``` so that the ```wallet/signer``` and ```workerName``` fields may be properly updated. By going through these steps, you can have all subpool members join the subpool and be able to send withdrawal and distribution requests. Members of the subpool will then take the ```holdingAddress``` field inside the config file and paste it into the wallet field of any Ergo miner of their choice. The mining program must connect to a mining pool supported by ergo-subpooling.

## The Holding Phase
Once enough mining has been done, the mining pool will send a payout to the subpool. At this point, each member of the mining pool may send a withdraw request to the
smart contract protecting their funds. Each withdraw request contains information about the subpool's state. This information consists of the number of share's submitted
by each subpool member, along with the total share's submitted by the entire pool. When all members of the subpool have sent a withdraw request, the next phase begins.

## The Consensus Phase
At this point, all member's have submitted their withdraw requests to the smart contract. Now, any member of the subpool may enter the ```distribute``` command into their
subpooling program. This will cause the smart contract to take the average of each subpool state sent by each member and reach a consensus about the shares submitted by each member of the subpool. This consensus will then be used to figure out how much ERG each member of the subpool gets from the total payout. Members of the subpool are paid according to the proportion of shares they submitted to the subpool.

## An Example
Alice and Bob are both mining to Herominers and decide to make a subpool. They go through the program instructions and then mine to the subpool for a few days until they get their payout of 0.5 ERG. Alice decides to send a withdraw request first, she enters the ```withdraw``` command which pings Herominers for information about the subpool. Herominers then sends the information back to Alice's subpooling program. This information is then sent to the smart contract protecting Alice and Bob's funds. The information consists of 3 things: Alice's share number, Bob's share number, and the total share number. 

Lets say during Alice's withdraw request, the information she got was:

```[50, 25, 75]```

A day later, Bob sends his withdraw request which has this information from the mining pool: 

```[100, 50, 150]```

Now that both Bob and Alice have sent withdraw requests, either Bob or Alice can enter the ```distribute``` command. Bob sends this command to the smart contract which takes the average from both withdraw requests and comes to a consensus about how many shares Alice or Bob sent. In this case the consensus will look like this:

```[75, 37.5, 112.5]```

From this consensus, the smart contract will determine how much ERG each member of the subpool will get. In this case, Alice will get:

```(75 / 112.5) * (0.5 - (MinTxFee / 2)) = ~0.32 ERG```

While Bob will get:

```(37.5 / 112.5) * (0.5 - (MinTxFee / 2)) = ~0.16 ERG```

The transaction fee is split up evenly between each member of the subpool.

# How To Run
To run, simply download the jar and run 
```java -jar ergo-subpooling-0.5.jar``` 
in command line or terminal.
### [Download Here](https://github.com/K-Singh/ergo-subpooling/raw/master/ergo-subpooling-0.5.jar)
If it says you need a subpool_config.json file, look at the one given [here](https://github.com/K-Singh/ergo-subpooling/blob/309a5e7d957a5455a8856d4ef251ab80c757b1d9/subpool_config.json) in the repository as an example.

Alternatively, you may clone the repository and run: 
```
cd ergo-subpooling
sbt assembly
```
In this way you can get around running a precompiled jar.

## Instructions
Once the program is running, you can start working towards your subpool. First, you should go use the ```wallets``` and ```new```commands to create a new wallet/signer.
A wallet/signer is necessary to prove that you own one of the addresses in your subpool. The wallet/signer is stored in an encrypted file. If you feel unsafe using your
main wallet, I would suggest making a new wallet just for subpooling. Please make sure the mneumonic and possible mneumonic password for your wallet/signer is stored
somewhere safe. Also make sure you remember the encryption password to access your wallet/signer.

After this, you can go back to the main menu and type ```create``` to create a new subpool. The program will first ask for the wallet/signer name to be using, along
with the corresponding encryption password. Once filled, you may type or paste in each address that's part of your subpool(The wallet address that corresponds to your
mneumonic should be a part of these). You must then enter an associated worker name for each address. Order matters, the first worker name belongs to the first address
and so on.

Finally you will be asked one more time to confirm your worker name. You will then enter your pool's payout information. Once this is done you may enter ```default```
or some ```config.json```. This will be where your subpool will be saved. ```default``` will save your subpool to ```subpool_config.json```.

Once this has been done, you may send your config file to everyone else in your subpool. They can then use the ```load``` and ```join``` commands to input their
unique worker names and associate their wallets/signers to this subpool.

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
The only thing left to do after this is to enter the ```holdingAddress``` into their mining software. For example, if Member One
used NBMiner to mine, they would simply enter this into their NBMiner's ```start_ergo.bat```:

```
nbminer -a ergo -o stratum+tcp://ca.ergo.herominers.com:1180 -u TkfL4THLVGRDihCwCZFho6KRfJKasEbdCUoGjaZfgd7UKdaoVByxNHYxb7J5tjdiGEFF4nAcJpgNKmduQE1WsGQmSTm3ixrqsSFKYV6c75TRSWixSKkyFgHqaXCsecQuAjaCq7DnRtHRMMtmgybL2mSJimmxPGx7hgRLTkRi4AheKGDFEffA97suhDsrCNj5JB43BkHFvMQEVoUAAh8wMB5gNCQQ1w4iZenuynt6DuqXHjbSteRc8qongATsratfKBwYALYqTQbxfJEbirxwrQimzbwRzsJRyw7PkUy8QNNqV4VnKxeQJ9fE5seTDyAT4Z7JHFVCdaSct7mBSdDfw7Escv9PXpCX3s8jWDZHw.MemberOne_Worker
pause
```

Once this has been done, the subpool is completely set up and each member should have begun mining to it.

## Commands In Subpooling Shell
Command | Description 
--------|------------
*create* | Creates subpool using wallet/signer and inputted information. Subpool is then saved to config file.
*load*  | Loads subpool from default config.
*load [config.json]* | load subpool from specified config file.
*join* | Join subpool in config file. Input wallet/signer and worker name so that requests can be handled correctly.
*withdraw* | Send withdraw request to holding box. Once all members have sent a wd request, distribution may occur.
*distribute* | Distributes money amongst members of subpool. Only one member of the subpool needs to do this.
*wallets* | Enters wallet/signer mode.
*list* | List wallet/signers that you have made.
*new* | Create a new wallet/signer.

# Current Supported Mining Pools
https://enigmapool.com

https://ergo.herominers.com/

ergo-subpooling only supports two mining pools at the moment. The goal is to incorporate as many mining pools as possible, especially smaller ones so as to increase security
of the Ergo blockchain. If you own a mining pool and would like your pool to be supported, please message me or send a pull request.

## This project was made using Ergo-Appkit
You can find it here: https://github.com/ergoplatform/ergo-appkit

## This project was inspired by ErgoSmartPools
https://github.com/WilfordGrimley/ErgoSmartPools
