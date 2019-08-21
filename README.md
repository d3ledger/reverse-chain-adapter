# Reverse chain adapter
## Service overview 
Reverse chain adapter is a service that is used for handling crucial financial operations in Hyperledger Iroha. 
It uses RabbitMQ as middleware between Iroha clients and Iroha peer. 
With this service on, transactions are not sent directly to Iroha peer but stored in a dedicated persistent queue. 
Once a transaction is saved in the queue, the service will read the incoming transaction and try to send it to Iroha peer. 
The service will send the transaction back to the queue if Iroha peer is not online, forming a loop. 
The service will try to send every incoming transaction to Iroha peer unless it's not received no matter how many `send` attempts it might take. 
The service will read all the incoming transactions from the queue on start-up and try to handle them if the service was offline by the time transaction was sent to RabbitMQ.
## Configuration file overview
Reverse chain adapter uses `reverse-chain-adapter.properties` as a default configuration file that is located in the `resources` folder inside the project. However, every configuration item could be changed through environmental variables. 

- `reverse-chain-adapter.rmqHost` - RabbitMQ host
- `reverse-chain-adapter.rmqPort` - RabbitMQ port
- `reverse-chain-adapter.transactionQueueName` - name of queue tha will be used to persist incoming transactions. The values equals to `reverse_adapter_queue` by default.
- `reverse-chain-adapter.irohaHost` - Iroha hostname
- `reverse-chain-adapter.irohaPort` - Iroha port
- `reverse-chain-adapter.healthCheckPort` - health check port

## How to run
Reverse chain-adapter may be run as a docker container using the following `docker-compose` instructions:

```
rmq:
  image: rabbitmq:3-management
  container_name: rmq
  ports:
    - 8181:15672
    - 5672:5672

reverse-chain-adapter:
  image: nexus.iroha.tech:19002/d3-deploy/reverse-chain-adapter:develop
  container_name: reverse-chain-adapter
  restart: on-failure
  depends_on:
    - iroha
    - rmq
```

Or it can be run programmatically right in your code:
```
ReverseChainAdapter(reverseChainAdapterConfig, irohaAPI).init()
    .fold(
        { println("Reverse chain adapter has been started") },
        { ex ->
            ex.printStackTrace()
            System.exit(1)
        })
```
In this case no health check endpoint for the adapter will be run, so setting `healthCheckPort` value won't make any effect.
  
## How to use
`com.d3.reverse.client.ReliableIrohaConsumerImpl` is the class used as a client for the service. The class may be obtained via [Jitpack](https://jitpack.io/#d3ledger/reverse-chain-adapter):

```groovy
implementation "com.github.d3ledger.reverse-chain-adapter:reverse-chain-adapter-client:$reverse_chain_adapter_client_version"
``` 
The client just sends your transaction to RabbitMQ and subscribes to its status. The client will "re-subscribe" to the transaction's status if Iroha is not online. If you are not interested in the transaction's status simply set the `fireAndForget` value to `true`. 

**Warning**. The client cannot send batches yet, so calling `send(lst: List<Transaction>)` or `send(lst: Iterable<TransactionOuterClass.Transaction>)` throws `UnsupportedOperationException`.

You may use the following dependency if you want the service to be run programmatically, rather than as a separate Docker container:
```groovy
implementation "com.github.d3ledger.reverse-chain-adapter:reverse-chain-adapter:$reverse_chain_adapter_client"
``` 
