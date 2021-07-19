# Bisq API Beta Testing Guide

This guide explains how Bisq Api beta testers can quickly get a test harness running, watch a regtest trade simulation,
and use the CLI to execute trades between Bob and Alice.

Knowledge of Git, Java, and installing bitcoin-core is required.

## System Requirements

**Hardware**:  A reasonably fast development machine is recommended, with at least 16 Gb RAM and 8 cores.
None of the headless apps use a lot of RAM, but build times can be long on machines with less RAM and fewer cores.
In addition, a slow machine may run into race conditions if asynchronous wallet changes are not persisted to disk
fast enough.  Test harness startup and shutdown times may also not happen fast enough, and require test harness
option adjustments to compensate.

**OS**:  Linux or Mac OSX

**Shell**:  Bash

**Java SDK**:  Version 10, 11, or 12

**Bitcoin-Core**:  Version 0.19, 0.20, or 0.21

**Git Client**

## Clone and Build Source Code

Beta testing can be done with no knowledge of how git works, but you need a git client to get the source code.

Clone the Bisq master branch into a project folder of your choice.  In this document, the root project folder is
called `api-beta-test`.
```
$ git clone https://github.com/bisq-network/bisq.git api-beta-test
```

Change your current working directory to `api-beta-test`, build the source, and download / install Bisq’s
pre-configured DAO / dev / regtest setup files.
```
$ cd api-beta-test
$ ./gradlew clean build :apitest:installDaoSetup -x test    # if you want to skip Bisq tests
$ ./gradlew clean build :apitest:installDaoSetup            # if you want to run Bisq tests
```

## Running Api Test Harness

If your bitcoin-core binaries are in your system `PATH`, start bitcoind in regtest-mode, Bisq seednode and arbitration
node daemons, plus Bob & Alice daemons in a bash terminal with the following bash command:
```
$ ./bisq-apitest --apiPassword=xyz \
        --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon \
        --shutdownAfterTests=false
```

If your bitcoin-core binaries are not in your system `PATH`, you can specify the bitcoin-core bin directory with the
`-–bitcoinPath=<path>` option:
```
$ ./bisq-apitest --apiPassword=xyz \
        --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon \
        --shutdownAfterTests=false \
        --bitcoinPath=<bitcoin-core-home>/bin
```

If your bitcoin-core binaries are not statically linked to your BerkleyDB library, you can specify the path to it
with the `–-berkeleyDbLibPath=<path>` option:
```
$ ./bisq-apitest --apiPassword=xyz \
        --supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon \
        --shutdownAfterTests=false \
        --bitcoinPath=<bitcoin-core-home>/bin \
        --berkeleyDbLibPath=<lib-berkleydb-path>
```

Alternatively, you can specify any or all of these bisq-apitest options in a properties file located in
`apitest/src/main/resources/apitest.properties`.

In this example, a beta tester uses the `apitest.properties` below, instead of `bisq-cli` options.
```
supportingApps=bitcoind,seednode,arbdaemon,alicedaemon,bobdaemon
apiPassword=xyz
shutdownAfterTests=false
bitcoinPath=/home/beta-tester/path-to-my-bitcoin-core/bin
```

Start up the test harness with without command options:
```
$ ./bisq-apitest
```

If you edit  `apitest.properties`, do not forget to re-build the source.  You do not need to do a full clean and
build, or run tests.  The following build command should finish quickly.
```
$ ./gradlew build :apitest:installDaoSetup -x test
```

You should see the test harness startup bitcoin-core and other Bisq daemons in your console, run a
`bitcoin-cli getwalletinfo` command, and generate a regtest btc block.

After the test harness tells you how to shut it down by entering `^C`, the test harness is ready to use.

## Running Trade Simulation Script

_Warning:  again, it is assumed the beta tester has a reasonably fast machine, or the scripted wait times -- for
the other side to perform his step in the protocol, and for btc block generation and asynchronous processing
of new btc blocks by test daemons -- may not be long enough._

### System Requirements

Same as described at the top of this document, but your bitcoin-core’s `bitcoin-cli` binary must be in the system
`PATH`.  (The script generates regtest blocks with it.)

### Description

The regtest trade simulation script `apitest/scripts/trade-simulation.sh` is a useful introduction to the Bisq Api.
The bash script’s output is intended to serve as a tutorial, showing how the CLI can be used to create payment
accounts for Bob and Alice, create an offer, take the offer, and complete a trade.
(The bash script itself is not intended to be as useful as the output.)  The output is generated too quickly to
follow in real time, so let the script complete before studying the output from start to finish.

The script takes four options:
```
-d=<direction>          The trade direciton, BUY or SELL.
-c=<country>            The two letter country code, US, FR, AT, RU, etc.
-f=<fixed-price>        The offer’s fixed price.
    OR  (-f and -m options mutually exclusive, use one or the other)
-m=<margin-from-price>  The offer’s margin (%) from market price.
-a=<btc-amount>         The amount of btc to buy or sell.
```

### Examples

This simulation creates US / USD face-to-face payment accounts for Bob and Alice.  Alice (always the trade maker)
creates a SELL / USD offer for the amount of 0.1 BTC, at a price 2% below the current market price.
Bob (always the taker), will use his face-to-face account to take the offer, then the two sides will complete
the trade, checking their trade status along the way, and their BSQ / BTC balances when the trade is closed.
```
$ apitest/scripts/trade-simulation.sh    -d sell -c us -m 2.00 -a 0.1
```

In the next example, Bob and Alice create Austrian face-to-face payment accounts.  Alice creates a BUY/ EUR
offer to buy 0.125 BTC at a fixed price of  30,800  EUR.
```
$ apitest/scripts/trade-simulation.sh -d buy -c at -f 30800  -a 0.125
```

## Manual Testing

The test harness used by the simulation script described in the previous section can also be used for manual CLI
testing, and you can leave it running as you try the commands described below.

The Api’s default server listening port is `9998`, and you do not need to specify a `–port=<port>` option in a
CLI command unless you change the server’s `–apiPort=<listening-port>`.   In the test harness, Alice’s Api port is
`9998`, Bob’s is `9999`.  When you manually test the Api using the test harness, be aware of the port numbers being
used in the CLI commands, so you know which server (Bob’s or Alice’s) the CLI is sending requests to.

### CLI Help

Useful information can be found using the CLI’s `--help` option.

For list of supported CLI commands:
```
$ ./bisq-cli  --help	(the –password option is not needed because there is no server request)
```

For help with a specific CLI command:
```
$ ./bisq-cli --password=xyz --help getbalance
OR
$ ./bisq-cli --password=xyz  getbalance --help
```
The position of `--help` option does not matter.  If a supported positional command option is present,
method help will be returned from the server.  Also note an api password is required to get help from the server.

### Working With Encrypted Wallet

There is no need to secure your regtest Bisq wallet with an encryption password when running these examples,
but you should encrypt your mainnet wallet as you probably already do when using the Bisq UI to transact in
real BTC.  This section explains how to encrypt your Bisq wallet with the CLI, and unlock it before performing wallet
related operations such as creating and taking offers, checking balances, and sending BSQ and BTC to external wallets.

Encrypt your wallet with a password:
```
$ ./bisq-cli --password=xyz setwalletpassword --wallet-password=<wallet-password>
```

Set a new password on your already encrypted wallet:
```
$ ./bisq-cli --password=xyz setwalletpassword --wallet-password=<wallet-password> \
    --new-wallet-password=<new-wallet-password>
```

Unlock your password encrypted wallet for N seconds before performing sensitive wallet operations:
```
$ ./bisq-cli --password=xyz unlockwallet --wallet-password=<wallet-password> --timeout=<seconds>
```
You can override a `timeout` before it expires by calling `unlockwallet` again.

Lock your wallet before the `unlockwallet` timeout expires:
```
$ ./bisq-cli --password=xyz lockwallet
```

### Checking Balances

Show full BSQ and BTC wallet balance information:
```
$ ./bisq-cli --password=xyz --port=9998  getbalance
```

Show full BSQ wallet balance information:
```
$ ./bisq-cli --password=xyz --port=9999 getbalance --currency-code=bsq
```
_Note:  The example above is asking for Bob’s balance (using port `9999`), not Alice’s balance._

Show Bob’s full BTC wallet balance information:
```
$ ./bisq-cli --password=xyz --port=9999 getbalance --currency-code=btc
```

### Funding a Bisq Wallet

#### Receiving BTC

To receive BTC from an external wallet, find an unused BTC address (with a zero balance) to receive the BTC.
```
$ ./bisq-cli --password=xyz --port=9998 getfundingaddresses
```
You can check a block explorer for the status of a transaction, or you can check your Bisq BTC wallet address directly:
```
$ ./bisq-cli --password=xyz --port=9998 getaddressbalance --address=<btc-address>
```

#### Receiving BSQ
To receive BSQ from an external wallet, find an unused BSQ address:
```
$ ./bisq-cli --password=xyz --port=9998 getunusedbsqaddress
```

Give the public address to the sender.  After the BSQ is sent, you can check block explorers for the status of
the transaction.  There is no support (yet) to check the balance of an individual BSQ address in your wallet,
but you can check your BSQ wallet’s balance to determine if the new funds have arrived:
```
$ ./bisq-cli --password=xyz --port=9999 getbalance --currency-code=bsq
```

### Sending BSQ and BTC to External Wallets

Below are commands for sending BSQ and BTC to external wallets.

Send BSQ:
```
$ ./bisq-cli --password=xyz --port=9998 sendbsq --address=<bsq-address> --amount=<bsq-amount>
```
_Note:  Sending BSQ to non-Bisq wallets is not supported and highly discouraged._

Send BSQ with a withdrawal transaction fee of 10 sats/byte:
```
$ ./bisq-cli --password=xyz --port=9998 sendbsq --address=<bsq-address> --amount=<bsq-amount>  --tx-fee-rate=10
```

Send BTC:
```
$ ./bisq-cli --password=xyz --port=9998 sendbtc --address=<btc-address> --amount=<btc-amount>
```
Send BTC with a withdrawal transaction fee of 20 sats/byte:
```
$ ./bisq-cli --password=xyz --port=9998 sendbtc --address=<btc-address> --amount=<btc-amount>  --tx-fee-rate=20
```

### Withdrawal Transaction Fees

If you have traded using the Bisq UI, you are probably aware of the default network bitcoin withdrawal transaction
fee and custom withdrawal transaction fee user preference in the UI’s setting view.  The Api uses these same
withdrawal transaction fee rates, and affords a third – as mentioned in the previous section -- withdrawal
transaction fee option in the `sendbsq` and `sendbtc` commands.  The `sendbsq` and `sendbtc` commands'
`--tx-fee-rate=<sats/byte>` options override both the default network fee rate, and your custom transaction fee
setting for the execution of those commands.

#### Using Default Network Transaction Fee

If you have not set your custom withdrawal transaction fee setting, the default network transaction fee will be used
when withdrawing funds.  In either case, you can check the current (default or custom) withdrawal transaction fee rate:
```
$ ./bisq-cli --password=xyz gettxfeerate
```
#### Setting Custom Transaction Fee Preference
To set a custom withdrawal transaction fee rate preference of 50 sats/byte:
```
$ ./bisq-cli --password=xyz settxfeerate --tx-fee-rate=50
```

#### Removing User’s Custom Transaction Fee Preference
To remove a custom withdrawal transaction fee rate preference, and revert to the network fee rate:
```
$ ./bisq-cli --password=xyz unsettxfeerate
```

### Creating Test Payment Accounts

Creating a payment account using the Api involves three steps:

1.  Find the payment-method-id  for the payment account type you wish to create.  For example, if you want to
    create a face-to-face type payment account, find the face-to-face  payment-method-id (`F2F`):
    ```
    $ ./bisq-cli --password=xyz --port=9998 getpaymentmethods
    ```

2.  Use the payment-method-id `F2F` found in the `getpaymentmethods` command output to create a blank payment account
    form:
    ```
    $ ./bisq-cli --password=xyz --port=9998 getpaymentacctform --payment-method-id=F2F
    ```
    This `getpaymentacctform` command generates a json file (form) for creating an `F2F` payment account,
    prints the file’s contents, and tells you where it is.  In this example, the sever created an `F2F` account
    form named `f2f_1612381824625.json`.


3.  Manually edit the json file, and use its path in the `createpaymentacct` command.
    ```
    $ ./bisq-cli --password=xyz --port=9998 createpaymentacct \
        --payment-account-form=f2f_1612381824625.json
    ```
    _Note:  You can rename the file before passing it to the  `createpaymentacct` command._

    The server will create and save the new payment account from details defined in the json file then
    return the new payment account to the CLI.  The CLI will display the account ID with other details
    in the console, but if you ever need to find a payment account ID, use the `getpaymentaccts` command:
    ```
    $ ./bisq-cli --password=xyz --port=9998 getpaymentaccts
    ```

### Creating Offers

The createoffer command is the Api's most complex command (so far), but CLI posix-style options are self-explanatory,
and CLI `createoffer` command help gives you specific information about each option.
```
$ ./bisq-cli --password=xyz --port=9998 createoffer --help
```

#### Examples

The `trade-simulation.sh` script described above is an easy way to figure out how to use this command.
In a previous example, Alice created a BUY/ EUR offer to buy 0.125 BTC at a fixed price of  30,800 EUR,
and pay the Bisq maker fee in BSQ.  Alice had already created an EUR face-to-face payment account with id
`f3c1ec8b-9761-458d-b13d-9039c6892413`, and used this `createoffer` command:
```
$ ./bisq-cli --password=xyz --port=9998 createoffer \
    --payment-account=f3c1ec8b-9761-458d-b13d-9039c6892413 \
    --direction=BUY \
    --currency-code=EUR \
    --amount=0.125 \
    --fixed-price=30800 \
    --security-deposit=15.0 \
    --fee-currency=BSQ
```

If Alice was in Japan, and wanted to create an offer to sell 0.125 BTC at 0.5% above the current market JPY price,
putting up a 15% security deposit, the `createoffer` command to do that would be:
```
$ ./bisq-cli --password=xyz --port=9998 createoffer \
    --payment-account=f3c1ec8b-9761-458d-b13d-9039c6892413 \
    --direction=SELL \
    --currency-code=JPY \
    --amount=0.125 \
    --market-price-margin=0.5 \
    --security-deposit=15.0 \
    --fee-currency=BSQ
```

The `trade-simulation.sh` script options that would generate the previous `createoffer` example is:
```
$ apitest/scripts/trade-simulation.sh -d sell -c jp -m 0.5  -a 0.125
```

### Browsing Your Own Offers

There are different commands to browse available offers you can take, and offers you created.

To see all offers you created with a specific direction (BUY|SELL) and currency (CAD|EUR|USD|...):
```
$ ./bisq-cli --password=xyz --port=9998 getmyoffers --direction=<BUY|SELL> --currency-code=<currency-code>
```

To look at a specific offer you created:
```
$ ./bisq-cli --password=xyz --port=9998 getmyoffer --offer-id=<offer-id>
```

### Browsing Available Offers

To see all available offers you can take, with a specific direction (BUY|SELL) and currency (CAD|EUR|USD|...):
```
$ ./bisq-cli --password=xyz --port=9998 getoffers --direction=<BUY|SELL> --currency-code=<currency-code>
```

To look at a specific, available offer you could take:
```
$ ./bisq-cli --password=xyz --port=9998 getoffer --offer-id=<offer-id>
```

### Removing An Offer

To cancel one of your offers:
```
$ ./bisq-cli --password=xyz --port=9998 canceloffer --offer-id=<offer-id>
```
The offer will be removed from other Bisq users' offer views, and paid transaction fees will be forfeited.

### Editing an Existing Offer

Offers you create can be edited in various ways:

- Disable or re-enable an offer.
- Change an offer's price model and disable (or re-enable) it.
- Change a market price margin based offer to a fixed price offer.
- Change a market price margin based offer's price margin.
- Change, set, or remove a trigger price on a market price margin based offer.
- Change a market price margin based offer's price margin and trigger price.
- Change a market price margin based offer's price margin and remove its trigger price.
- Change a fixed price offer to a market price margin based offer.
- Change a fixed price offer's fixed price.

_Note: the API does not support editing an offer's payment account._

The subsections below contain examples related to specific use cases.

#### Enable and Disable Offer

Existing offers you create can be disabled (removed from offer book) and re-enabled (re-published to offer book).

To disable an offer:
```
./bisq-cli --password=xyz --port=9998 editoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --enable=false
```

To enable an offer:
```
./bisq-cli --password=xyz --port=9998 editoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --enable=true
```

#### Change Offer Pricing Model
The `editoffer` command can be used to change an existing market price margin based offer to a fixed price offer,
and vice-versa.

##### Change Market Price Margin Based to Fixed Price Offer
Suppose you used `createoffer` to create a market price margin based offer as follows:
```
$ ./bisq-cli --password=xyz --port=9998 createoffer \
    --payment-account=f3c1ec8b-9761-458d-b13d-9039c6892413 \
    --direction=SELL \
    --currency-code=JPY \
    --amount=0.125 \
    --market-price-margin=0.5 \
    --security-deposit=15.0 \
    --fee-currency=BSQ
```
To change the market price margin based offer to a fixed price offer:
```
./bisq-cli --password=xyz --port=9998 editoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --fixed-price=3960000.5555
```

##### Change Fixed Price Offer to Market Price Margin Based Offer
Suppose you used `createoffer` to create a fixed price offer as follows:
```
$ ./bisq-cli --password=xyz --port=9998 createoffer \
    --payment-account=f3c1ec8b-9761-458d-b13d-9039c6892413 \
    --direction=SELL \
    --currency-code=JPY \
    --amount=0.125 \
    --fixed-price=3960000.0000 \
    --security-deposit=15.0 \
    --fee-currency=BSQ
```
To change the fixed price offer to a market price margin based offer:
```
./bisq-cli --password=xyz --port=9998 editoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --market-price-margin=0.5
```
Alternatively, you can also set a trigger price on the re-published, market price margin based offer.
A trigger price on a SELL offer causes the offer to be automatically disabled when the market price
falls below the trigger price.  In the `editoffer` example below, the SELL offer will be disabled when
the JPY market price falls below 3960000.0000.

```
./bisq-cli --password=xyz --port=9998 editoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --market-price-margin=0.5 \
    --trigger-price=3960000.0000
```
On a BUY offer, a trigger price causes the BUY offer to be automatically disabled when the market price
rises above the trigger price.

_Note: Disabled offers never automatically re-enable; they can only be manually re-enabled via
`editoffer --offer-id=<id> --enable=true`._

#### Remove Trigger Price
To remove a trigger price on a market price margin based offer, set the trigger price to 0:
```
./bisq-cli --password=xyz --port=9998 editoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --trigger-price=0
```

#### Change Disabled Offer's Pricing Model and Enable It
You can use `editoffer` to simultaneously change an offer's price details and disable or re-enable it.

Suppose you have a disabled, fixed price offer, and want to change it to a market price margin based offer, set
a trigger price, and re-enable it:
```
./bisq-cli --password=xyz --port=9998 editoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --market-price-margin=0.5 \
    --trigger-price=3960000.0000 \
    --enable=true
```

### Taking Offers

Taking an available offer involves two CLI commands: `getoffers` and `takeoffer`.

A CLI user browses available offers with the getoffers command.  For example, the user browses SELL / EUR offers:
```
$ ./bisq-cli --password=xyz --port=9998  getoffers --direction=SELL --currency-code=EUR
```

And takes one of the available offers with an EUR payment account ( id `fe20cdbd-22be-4b8a-a4b6-d2608ff09d6e`)
with the `takeoffer` command:
```
$ ./bisq-cli --password=xyz --port=9998 takeoffer \
    --offer-id=83e8b2e2-51b6-4f39-a748-3ebd29c22aea \
    --payment-account=fe20cdbd-22be-4b8a-a4b6-d2608ff09d6e \
    --fee-currency=btc
```
The taken offer will be used to create a trade contract.  The next section describes how to use the Api to execute
the trade.

### Completing Trade Protocol

The first step in the Bisq trade protocol is completed when a `takeoffer` command successfully creates a new trade from
the taken offer.  After the Bisq nodes prepare the trade, its status can be viewed with the `gettrade` command:
```
$ ./bisq-cli --password=xyz --port=9998 gettrade --trade-id=<trade-id>
```
The `trade-id` is the same as the taken `offer-id`, but when viewing and interacting with trades, it is referred to as
the `trade-id`.  Note that the `trade-id` argument is a full `offer-id`, not a truncated `short-id` as displayed in the
Bisq UI.

You can also view the entire trade contract in `json` format by using the `gettrade` command's `--show-contract=true`
option:
```
$ ./bisq-cli --password=xyz --port=9998 gettrade --trade-id=<trade-id> --show-contract=true
```


The `gettrade` command’s output shows the state of the trade from initial preparation through completion and closure.
Output columns include:
```
Deposit Published           YES if the taker fee tx deposit has been broadcast to the network.
Deposit Confirmed           YES if the taker fee tx deposit has been confirmed by the network.
Fiat Sent                   YES if the buyer has sent a “payment started” message to seller.
Fiat Received               YES if the seller has sent a “payment received” message to buyer.
Payout Published            YES if the seller’s BTC payout tx has been broadcast to the network.
Withdrawn                   YES if the buyer’s BTC proceeds have been sent to an external wallet.
```
Trade status information informs both sides of a trade which steps have been completed, and which step to perform next.
It should be frequently checked by both sides before proceeding to the next step of the protocol.

_Note:  There is some delay after a new trade is created due to the time it takes for a taker’s trade deposit fee
transaction to be published and confirmed on the bitcoin network.  Both sides of the trade can check the `gettrade`
output's `Deposit Published` and `Deposit Confirmed` columns to find out when this early phase of the trade protocol is
complete._

Once the taker fee transaction has been confirmed, payment can be sent, payment receipt confirmed, and the trade
protocol completed.  There are three CLI commands that must be performed in coordinated order by each side of the trade:
```
confirmpaymentstarted       Buyer sends seller a message confirming payment has been sent.
confirmpaymentreceived      Seller sends buyer a message confirming payment has been received.
keepfunds                   Keep trade proceeds in their Bisq wallets.
    OR
withdrawfunds               Send trade proceeds to an external wallet.
```
The last two mutually exclusive commands (`keepfunds` or `withdrawfunds`) may seem unnecessary, but they are critical
because they inform the Bisq node that a trade’s state can be set to `CLOSED`.  Please close out your trades with one
or the other command.

Each of the CLI commands above takes one argument:  `--trade-id=<trade-id>`:
```
$ ./bisq-cli --password=xyz --port=9998 confirmpaymentstarted --trade-id=<trade-id>
$ ./bisq-cli --password=xyz --port=9999 confirmpaymentreceived --trade-id=<trade-id>
$ ./bisq-cli --password=xyz --port=9998 keepfunds --trade-id=<trade-id>
$ ./bisq-cli --password=xyz --port=9999 withdrawfunds --trade-id=<trade-id> --address=<btc-address> [--memo=<"memo">]
```

## Shutting Down Test Harness

The test harness should cleanly shutdown all the background apps in proper order after entering ^C.

Once shutdown, all Bisq and bitcoin-core data files are left in the state they were in at shutdown time,
so they and logs can be examined after a test run.  All datafiles will be refreshed the next time the test harness
is started, so if you want to save datafiles and logs from a test run, copy them to a safe place first.
They can be found in `apitest/build/resources/main`.

