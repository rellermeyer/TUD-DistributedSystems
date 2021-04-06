package sgrub.smartcontracts.generated;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
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
public class StorageManager extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b50600280546001600160a01b031916331790556109ac806100326000396000f3fe608060405234801561001057600080fd5b50600436106100575760003560e01c806309f2bd031461005c57806335c07af014610071578063ae0d3e2714610084578063c432c457146100a2578063e6359f40146100b5575b600080fd5b61006f61006a366004610705565b6100c8565b005b61006f61007f366004610726565b61017d565b61008c610212565b6040516100999190610761565b60405180910390f35b61006f6100b0366004610624565b6102a4565b61006f6100c3366004610705565b610407565b6001600160c01b0319811660009081526020819052604090206001015460ff1615610142576001600160c01b031981166000818152602081905260409081902090517fccb79a8a9f3ec33a4189d30de745e5c47ed182185622ba2500d8a6250576158e91610135916107b4565b60405180910390a261017a565b60405133906001600160c01b03198316907fc7e6de367830ad130657352fe6da03a69f9695ed2bf3e862ea3a789ac75e443990600090a35b50565b6002546001600160a01b031633146101b05760405162461bcd60e51b81526004016101a79061085b565b60405180910390fd5b80516021146101fb5760405162461bcd60e51b8152602060048201526017602482015276446967657374206d75737420626520333320627974657360481b60448201526064016101a7565b805161020e906001906020840190610464565b5050565b606060018054610221906108fe565b80601f016020809104026020016040519081016040528092919081815260200182805461024d906108fe565b801561029a5780601f1061026f5761010080835404028352916020019161029a565b820191906000526020600020905b81548152906001019060200180831161027d57829003601f168201915b5050505050905090565b6002546001600160a01b031633146102ce5760405162461bcd60e51b81526004016101a79061085b565b80516021146103195760405162461bcd60e51b8152602060048201526017602482015276446967657374206d75737420626520333320627974657360481b60448201526064016101a7565b805161032c906001906020840190610464565b5060005b835181101561040157604051806040016040528084838151811061036457634e487b7160e01b600052603260045260246000fd5b602002602001015181526020016001151581525060008086848151811061039b57634e487b7160e01b600052603260045260246000fd5b6020908102919091018101516001600160c01b0319168252818101929092526040016000208251805191926103d592849290910190610464565b50602091909101516001909101805460ff19169115159190911790556103fa81610939565b9050610330565b50505050565b6002546001600160a01b031633146104315760405162461bcd60e51b81526004016101a79061085b565b6001600160c01b0319811660009081526020819052604081209061045582826104e8565b50600101805460ff1916905550565b828054610470906108fe565b90600052602060002090601f01602090048101928261049257600085556104d8565b82601f106104ab57805160ff19168380011785556104d8565b828001600101855582156104d8579182015b828111156104d85782518255916020019190600101906104bd565b506104e4929150610520565b5090565b5080546104f4906108fe565b6000825580601f10610506575061017a565b601f01602090049060005260206000209081019061017a91905b5b808211156104e45760008155600101610521565b600082601f830112610545578081fd5b8135602061055a610555836108da565b6108a9565b82815281810190858301855b8581101561058f5761057d898684358b01016105b9565b84529284019290840190600101610566565b5090979650505050505050565b80356001600160c01b0319811681146105b457600080fd5b919050565b600082601f8301126105c9578081fd5b813567ffffffffffffffff8111156105e3576105e3610960565b6105f6601f8201601f19166020016108a9565b81815284602083860101111561060a578283fd5b816020850160208301379081016020019190915292915050565b600080600060608486031215610638578283fd5b833567ffffffffffffffff8082111561064f578485fd5b818601915086601f830112610662578485fd5b81356020610672610555836108da565b82815281810190858301838502870184018c101561068e57898afd5b8996505b848710156106b7576106a38161059c565b835260019690960195918301918301610692565b50975050870135925050808211156106cd578384fd5b6106d987838801610535565b935060408601359150808211156106ee578283fd5b506106fb868287016105b9565b9150509250925092565b600060208284031215610716578081fd5b61071f8261059c565b9392505050565b600060208284031215610737578081fd5b813567ffffffffffffffff81111561074d578182fd5b610759848285016105b9565b949350505050565b6000602080835283518082850152825b8181101561078d57858101830151858201604001528201610771565b8181111561079e5783604083870101525b50601f01601f1916929092016040019392505050565b60006020808352818454836002820490506001808316806107d657607f831692505b8583108114156107f457634e487b7160e01b87526022600452602487fd5b87860183815260200181801561081157600181146108225761084c565b60ff1986168252878201965061084c565b60008b815260209020895b868110156108465781548482015290850190890161082d565b83019750505b50949998505050505050505050565b6020808252602e908201527f4f6e6c792074686520636f6e7472616374206f776e65722063616e2063616c6c60408201526d103a3434b990333ab731ba34b7b760911b606082015260800190565b604051601f8201601f1916810167ffffffffffffffff811182821017156108d2576108d2610960565b604052919050565b600067ffffffffffffffff8211156108f4576108f4610960565b5060209081020190565b60028104600182168061091257607f821691505b6020821081141561093357634e487b7160e01b600052602260045260246000fd5b50919050565b600060001982141561095957634e487b7160e01b81526011600452602481fd5b5060010190565b634e487b7160e01b600052604160045260246000fdfea2646970667358221220ffbd44a1dfbdf8e6ed9f7102aff8defa5b7197b02b1633f217fa394899f1a3a964736f6c63430008020033";

    public static final String FUNC_GGET = "gGet";

    public static final String FUNC_GETDIGEST = "getDigest";

    public static final String FUNC_REMOVE = "remove";

    public static final String FUNC_UPDATE = "update";

    public static final String FUNC_UPDATEDIGESTONLY = "updateDigestOnly";

    public static final Event DELIVER_EVENT = new Event("deliver", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes8>(true) {}, new TypeReference<DynamicBytes>() {}));
    ;

    public static final Event REQUEST_EVENT = new Event("request", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes8>(true) {}, new TypeReference<Address>(true) {}));
    ;

    @Deprecated
    protected StorageManager(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected StorageManager(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected StorageManager(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected StorageManager(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<DeliverEventResponse> getDeliverEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(DELIVER_EVENT, transactionReceipt);
        ArrayList<DeliverEventResponse> responses = new ArrayList<DeliverEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            DeliverEventResponse typedResponse = new DeliverEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.key = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.value = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
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
                typedResponse.value = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<DeliverEventResponse> deliverEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(DELIVER_EVENT));
        return deliverEventFlowable(filter);
    }

    public List<RequestEventResponse> getRequestEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(REQUEST_EVENT, transactionReceipt);
        ArrayList<RequestEventResponse> responses = new ArrayList<RequestEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RequestEventResponse typedResponse = new RequestEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.key = (byte[]) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.sender = (String) eventValues.getIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<RequestEventResponse> requestEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, RequestEventResponse>() {
            @Override
            public RequestEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues = extractEventParametersWithLog(REQUEST_EVENT, log);
                RequestEventResponse typedResponse = new RequestEventResponse();
                typedResponse.log = log;
                typedResponse.key = (byte[]) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.sender = (String) eventValues.getIndexedValues().get(1).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<RequestEventResponse> requestEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(REQUEST_EVENT));
        return requestEventFlowable(filter);
    }

    public RemoteFunctionCall<TransactionReceipt> gGet(byte[] key) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_GGET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes8(key)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<byte[]> getDigest() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_GETDIGEST, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeRemoteCallSingleValueReturn(function, byte[].class);
    }

    public RemoteFunctionCall<TransactionReceipt> remove(byte[] key) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_REMOVE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes8(key)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> update(List<byte[]> keys, List<byte[]> values, byte[] _digest) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_UPDATE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Bytes8>(
                        org.web3j.abi.datatypes.generated.Bytes8.class,
                        org.web3j.abi.Utils.typeMap(keys, org.web3j.abi.datatypes.generated.Bytes8.class)), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.DynamicBytes>(
                        org.web3j.abi.datatypes.DynamicBytes.class,
                        org.web3j.abi.Utils.typeMap(values, org.web3j.abi.datatypes.DynamicBytes.class)), 
                new org.web3j.abi.datatypes.DynamicBytes(_digest)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<TransactionReceipt> updateDigestOnly(byte[] _digest) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_UPDATEDIGESTONLY, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicBytes(_digest)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static StorageManager load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new StorageManager(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static StorageManager load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new StorageManager(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static StorageManager load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new StorageManager(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static StorageManager load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new StorageManager(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<StorageManager> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(StorageManager.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<StorageManager> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(StorageManager.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<StorageManager> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(StorageManager.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<StorageManager> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(StorageManager.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class DeliverEventResponse extends BaseEventResponse {
        public byte[] key;

        public byte[] value;
    }

    public static class RequestEventResponse extends BaseEventResponse {
        public byte[] key;

        public String sender;
    }
}
