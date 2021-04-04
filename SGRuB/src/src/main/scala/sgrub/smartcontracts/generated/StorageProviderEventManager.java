package sgrub.smartcontracts.generated;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.4.1.
 */
@SuppressWarnings("rawtypes")
public class StorageProviderEventManager extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b50600080546001600160a01b0319163317905561026a806100326000396000f3fe608060405234801561001057600080fd5b506004361061002b5760003560e01c80636464738814610030575b600080fd5b61004361003e366004610100565b610045565b005b6000546001600160a01b031633146100ba5760405162461bcd60e51b815260206004820152602e60248201527f4f6e6c792074686520636f6e7472616374206f776e65722063616e2063616c6c60448201526d103a3434b990333ab731ba34b7b760911b606482015260840160405180910390fd5b816001600160c01b0319167fccb79a8a9f3ec33a4189d30de745e5c47ed182185622ba2500d8a6250576158e826040516100f491906101cb565b60405180910390a25050565b60008060408385031215610112578182fd5b82356001600160c01b031981168114610129578283fd5b9150602083013567ffffffffffffffff80821115610145578283fd5b818501915085601f830112610158578283fd5b81358181111561016a5761016a61021e565b604051601f8201601f19908116603f011681019083821181831017156101925761019261021e565b816040528281528860208487010111156101aa578586fd5b82602086016020830137856020848301015280955050505050509250929050565b6000602080835283518082850152825b818110156101f7578581018301518582016040015282016101db565b818111156102085783604083870101525b50601f01601f1916929092016040019392505050565b634e487b7160e01b600052604160045260246000fdfea264697066735822122064b1e0a86b6d378831f6bdb59539b48614c9b1da243980c173a186d6969f1e2364736f6c63430008020033";

    public static final String FUNC_EMITDELIVER = "emitDeliver";

    public static final Event DELIVER_EVENT = new Event("deliver", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes8>(true) {}, new TypeReference<DynamicBytes>() {}));
    ;

    @Deprecated
    protected StorageProviderEventManager(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected StorageProviderEventManager(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected StorageProviderEventManager(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected StorageProviderEventManager(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<DeliverEventResponse> getDeliverEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DELIVER_EVENT, transactionReceipt);
        ArrayList<DeliverEventResponse> responses = new ArrayList<DeliverEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DeliverEventResponse typedResponse = new DeliverEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.key = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.proof = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<DeliverEventResponse> deliverEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, DeliverEventResponse>() {
            @Override
            public DeliverEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(DELIVER_EVENT, log);
                DeliverEventResponse typedResponse = new DeliverEventResponse();
                typedResponse.log = log;
                typedResponse.key = (byte[]) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.proof = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DeliverEventResponse> deliverEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DELIVER_EVENT));
        return deliverEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> emitDeliver(byte[] key, byte[] proof) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_EMITDELIVER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes8(key), 
                new org.web3j.abi.datatypes.DynamicBytes(proof)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static StorageProviderEventManager load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new StorageProviderEventManager(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static StorageProviderEventManager load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new StorageProviderEventManager(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static StorageProviderEventManager load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new StorageProviderEventManager(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static StorageProviderEventManager load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new StorageProviderEventManager(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<StorageProviderEventManager> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(StorageProviderEventManager.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<StorageProviderEventManager> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(StorageProviderEventManager.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<StorageProviderEventManager> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(StorageProviderEventManager.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<StorageProviderEventManager> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(StorageProviderEventManager.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class DeliverEventResponse extends BaseEventResponse {
        public byte[] key;

        public byte[] proof;
    }
}
