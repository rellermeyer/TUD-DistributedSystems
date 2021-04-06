package sgrub

import java.math.BigInteger

import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.StaticGasProvider

package object chain {
  def gasProvider = new StaticGasProvider(new BigInteger("1000000000"), new BigInteger("8000000"))
  def web3: Web3j = Web3j.build(new HttpService(config.getString("sgrub.networkAddress")))
}
