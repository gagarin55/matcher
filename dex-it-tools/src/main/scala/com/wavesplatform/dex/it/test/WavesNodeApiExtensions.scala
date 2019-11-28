package com.wavesplatform.dex.it.test

import cats.Id
import com.wavesplatform.dex.it.api.NodeApi
import com.wavesplatform.transaction.Transaction

trait WavesNodeApiExtensions {
  this: HasWavesNode =>

  protected def broadcastAndAwait(txs: Transaction*): Unit = broadcastAndAwait(wavesNode1Api, txs: _*)

  protected def broadcastAndAwait(wavesNodeApi: NodeApi[Id], txs: Transaction*): Unit = {
    txs.map(wavesNodeApi.broadcast)
    txs.foreach(tx => wavesNodeApi.waitForTransaction(tx))
  }
}