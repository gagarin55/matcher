package com.wavesplatform.dex.api.websockets

import akka.http.scaladsl.model.ws.TextMessage
import cats.syntax.option._
import com.wavesplatform.dex.api.websockets.WsOrderBook.WsSide
import com.wavesplatform.dex.domain.model.Denormalization._
import com.wavesplatform.dex.fp.MayBeEmpty
import com.wavesplatform.dex.json.Implicits.JsPathOps
import com.wavesplatform.dex.model.{LastTrade, LevelAgg}
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.collection.immutable.TreeMap

case class WsOrderBook(asks: WsSide, bids: WsSide, lastTrade: Option[WsLastTrade], updateId: Long, timestamp: Long = System.currentTimeMillis)
    extends WsMessage {
  override def toStrictTextMessage: TextMessage.Strict = TextMessage.Strict(WsOrderBook.wsOrderBookStateFormat.writes(this).toString)
  override val tpe: String                             = "ob"
}

object WsOrderBook {

  def wsUnapply(arg: WsOrderBook): Option[(String, Long, Long, WsSide, WsSide, Option[WsLastTrade])] =
    (arg.tpe, arg.timestamp, arg.updateId, arg.asks, arg.bids, arg.lastTrade).some

  type WsSide = TreeMap[Double, Double]

  private val asksOrdering: Ordering[Double] = (x: Double, y: Double) => Ordering.Double.compare(x, y)
  private val bidsOrdering: Ordering[Double] = (x: Double, y: Double) => -Ordering.Double.compare(x, y)

  val empty: WsOrderBook =
    WsOrderBook(
      asks = TreeMap.empty(asksOrdering),
      bids = TreeMap.empty(bidsOrdering),
      lastTrade = None,
      updateId = 0
    )

  implicit val wsOrderBookStateFormat: Format[WsOrderBook] = (
    (__ \ "T").format[String] and
      (__ \ "_").format[Long] and
      (__ \ "U").format[Long] and
      (__ \ "a").formatMayBeEmpty[WsSide](sideFormat(asksOrdering), sideMayBeEmpty(asksOrdering)) and
      (__ \ "b").formatMayBeEmpty[WsSide](sideFormat(bidsOrdering), sideMayBeEmpty(bidsOrdering)) and
      (__ \ "t").formatNullable[WsLastTrade]
  )(
    (_, timestamp, uid, asks, bids, lastTrade) => WsOrderBook(asks, bids, lastTrade, uid, timestamp),
    unlift(WsOrderBook.wsUnapply)
  )

  private val priceAmountFormat = Format(
    fjs = Reads.Tuple2R(doubleAsStringFormat, doubleAsStringFormat),
    tjs = Writes.Tuple2W(doubleAsStringFormat, doubleAsStringFormat)
  )

  private def sideFormat(pricesOrdering: Ordering[Double]): Format[WsSide] = Format(
    fjs = Reads {
      case JsArray(pairs) =>
        pairs.zipWithIndex.foldLeft[JsResult[WsSide]](JsSuccess(TreeMap.empty[Double, Double](pricesOrdering))) {
          case (r: JsError, _) => r

          case (JsSuccess(r, _), (pair, i)) =>
            for {
              (price, amount) <- pair.validate(priceAmountFormat)
              _               <- if (r.contains(price)) JsError(JsPath \ i \ 0, s"Side contains price $price twice") else JsSuccess(())
            } yield r.updated(price, amount)

          case (_, (_, i)) => JsError(JsPath \ i, "Can't read as price+amount pair")
        }
      case x => JsError(JsPath, s"Can't read Side from ${x.getClass.getName}")
    },
    tjs = Writes { xs =>
      JsArray(xs.map(priceAmountFormat.writes)(collection.breakOut))
    }
  )

  private def sideMayBeEmpty(ordering: Ordering[Double]): MayBeEmpty[WsSide] = new MayBeEmpty[WsSide] {
    override def isEmpty(x: WsSide): Boolean = x.isEmpty
    override def empty: WsSide               = TreeMap.empty(ordering)
  }

  def from(amountDecimals: Int,
           priceDecimals: Int,
           asks: Iterable[LevelAgg],
           bids: Iterable[LevelAgg],
           lt: Option[LastTrade],
           updateId: Long): WsOrderBook =
    WsOrderBook(
      asks = side(amountDecimals, priceDecimals, asks, asksOrdering),
      bids = side(amountDecimals, priceDecimals, bids, bidsOrdering),
      lastTrade = lt.map(lastTrade(amountDecimals, priceDecimals, _)),
      updateId = updateId
    )

  def lastTrade(amountDecimals: Int, priceDecimals: Int, x: LastTrade): WsLastTrade = WsLastTrade(
    price = denormalizePrice(x.price, amountDecimals, priceDecimals).toDouble,
    amount = denormalizeAmountAndFee(x.amount, amountDecimals).toDouble,
    side = x.side
  )

  def side(amountDecimals: Int, priceDecimals: Int, xs: Iterable[LevelAgg], ordering: Ordering[Double]): WsSide =
    TreeMap(
      xs.map { x =>
        denormalizePrice(x.price, amountDecimals, priceDecimals).toDouble ->
          denormalizeAmountAndFee(x.amount, amountDecimals).toDouble
      }.toSeq: _*
    )(ordering)
}
