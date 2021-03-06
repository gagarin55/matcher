package com.wavesplatform.dex.api.http

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import com.wavesplatform.dex.actors.OrderBookAskAdapter
import com.wavesplatform.dex.api
import com.wavesplatform.dex.api.MatcherResponse.toHttpResponse
import com.wavesplatform.dex.domain.asset.{Asset, AssetPair}
import com.wavesplatform.dex.market.AggregatedOrderBookActor.Depth
import com.wavesplatform.dex.model.MatcherModel.{DecimalsFormat, Denormalized}
import com.wavesplatform.dex.model.OrderBookResult
import com.wavesplatform.dex.time.Time
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class OrderBookHttpInfo(settings: OrderBookHttpInfo.Settings, askAdapter: OrderBookAskAdapter, time: Time, assetDecimals: Asset => Option[Int])(
    implicit ec: ExecutionContext) {
  private val marketStatusNotFound = toHttpResponse(
    api.SimpleResponse(StatusCodes.NotFound, Json.obj("message" -> "There is no information about this asset pair")))

  def getMarketStatus(assetPair: AssetPair): Future[HttpResponse] =
    askAdapter.getMarketStatus(assetPair).map {
      case Left(e) => toHttpResponse(api.OrderBookUnavailable(e))
      case Right(x) =>
        x match {
          case Some(x) => toHttpResponse(api.SimpleResponse(StatusCodes.OK, Json.toJsObject(x)))
          case None    => marketStatusNotFound
        }
    }

  def getHttpView(assetPair: AssetPair, format: DecimalsFormat, depth: Option[Depth]): Future[HttpResponse] =
    askAdapter.getHttpView(assetPair, format, settings.nearestBigger(depth)).map {
      case Right(x) => x.getOrElse(getDefaultHttpView(assetPair, format))
      case Left(e)  => toHttpResponse(api.OrderBookUnavailable(e))
    }

  private def getDefaultHttpView(assetPair: AssetPair, format: DecimalsFormat): HttpResponse = {
    val entity = OrderBookResult(time.correctedTime(), assetPair, Seq.empty, Seq.empty, assetPairDecimals(assetPair, format))
    HttpResponse(
      entity = HttpEntity(
        ContentTypes.`application/json`,
        OrderBookResult.toJson(entity)
      )
    )
  }

  private def assetPairDecimals(assetPair: AssetPair, format: DecimalsFormat): Option[(Depth, Depth)] = format match {
    case Denormalized => assetDecimals(assetPair.amountAsset).zip(assetDecimals(assetPair.priceAsset)).headOption
    case _            => None
  }
}

object OrderBookHttpInfo {
  case class Settings(depthRanges: List[Int], defaultDepth: Option[Int]) {
    def nearestBigger(to: Option[Int]): Int =
      to.orElse(defaultDepth)
        .flatMap(desiredDepth => depthRanges.find(_ >= desiredDepth))
        .getOrElse(depthRanges.max)
  }
}
