package com.wavesplatform.state2.diffs

import com.wavesplatform.state2._
import com.wavesplatform.state2.reader.SnapshotStateReader
import scorex.transaction.DataTransaction.{ParsedItem, TypedValue}
import scorex.transaction.{DataTransaction, ValidationError}

object DataTransactionDiff {

  def apply(state: SnapshotStateReader, height: Int)(tx: DataTransaction): Either[ValidationError, Diff] = {
    val sender = tx.sender.toAddress
    ///validate tx against state or rm state param
    Right(Diff(height, tx,
      portfolios = Map(sender -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty))))
//      accountData = Map(sender -> tx.data.map { case ParsedItem(k, t, v) => k -> TypedValue})))///
  }
}