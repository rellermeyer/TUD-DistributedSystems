# Requirements

Solidity compiler:
```shell
sudo add-apt-repository ppa:ethereum/ethereum
sudo apt-get update
sudo apt-get install solc
```

Web3j CLI:
```shell
curl -L get.web3j.io | sh && source ~/.web3j/source.sh
```

# Usage

Put your Solidity `<yourcontract>.sol` file in the `smartcontracts` folder.
Then, from the `smartcontracts` folder:
```shell
./build-generate-solidity.sh <yourcontract>
```

Example Scala code:
```scala
val web3 = Web3j.build(new HttpService());  // defaults to http://localhost:8545/
val credentials = WalletUtils.loadCredentials("password", "/path/to/walletfile")
val transactionManager = new RawTransactionManager(web3, credentials, 1234) // 1234 = chainId (Our chainId is 15)
val gasProvider = new StaticGasProvider(new BigInteger("<Gas price>"), new BigInteger("<Gas limit>"))

// Deploy contract
val contract = Greeter.deploy(
  web3, transactionManager, gasProvider, "Hi").send();
// If the contract was already deployed, instead...
val contract = Greeter.load("someaddress", web3, transactionManager, gasProvider)

// Call a procedure on the contract
contract.greet()
```