package com.wavesplatform.it.sync

import java.util.concurrent.ThreadLocalRandom

import com.dimafeng.testcontainers.KafkaContainer
import com.github.dockerjava.api.model.ContainerNetwork
import com.typesafe.config.{Config, ConfigFactory}
import com.wavesplatform.dex.api.websockets.{WsBalances, WsOrder}
import com.wavesplatform.dex.domain.asset.Asset
import com.wavesplatform.dex.domain.asset.Asset.Waves
import com.wavesplatform.dex.domain.model.Denormalization._
import com.wavesplatform.dex.domain.order.OrderType.SELL
import com.wavesplatform.dex.it.api.websockets.HasWebSockets
import com.wavesplatform.dex.model.{LimitOrder, OrderStatus}
import com.wavesplatform.it.MatcherSuiteBase

import scala.collection.JavaConverters._

class KafkaIssuesTestSuite extends MatcherSuiteBase with HasWebSockets {

  private val kafkaContainerName = "kafka"
  private val kafkaIp            = getIp(12)

  private val kafka: KafkaContainer =
    KafkaContainer().configure { k =>
      k.withNetwork(network)
      k.withNetworkAliases(kafkaContainerName)
      k.withCreateContainerCmdModifier { cmd =>
        cmd withName kafkaContainerName
        cmd withIpv4Address getIp(12)
      }
    }

  override protected val dexInitialSuiteConfig: Config = ConfigFactory.parseString(s"""waves.dex.price-assets = [ "$UsdId", "WAVES" ]""")

  override protected lazy val dexRunConfig: Config = ConfigFactory.parseString(
    s"""waves.dex.events-queue {
       |  type = kafka
       |  kafka {
       |    servers = "$kafkaIp:9092"
       |    topic = "dex-${ThreadLocalRandom.current.nextInt(0, Int.MaxValue)}"
       |  }
       |}""".stripMargin
  )

  override protected def beforeAll(): Unit = {
    wavesNode1.start()
    kafka.start()

    broadcastAndAwait(IssueUsdTx)
    dex1.start()
  }

  private def disconnectKafkaFromNetwork(): Unit = {
    kafka.dockerClient
      .disconnectFromNetworkCmd()
      .withContainerId(kafka.containerId)
      .withNetworkId(kafka.network.getId)
      .exec()
  }

  private def connectKafkaToNetwork(): Unit = {
    kafka.dockerClient
      .connectToNetworkCmd()
      .withContainerId(kafka.containerId)
      .withNetworkId(kafka.network.getId)
      .withContainerNetwork(
        new ContainerNetwork()
          .withIpamConfig(new ContainerNetwork.Ipam().withIpv4Address(kafkaIp))
          .withAliases(kafka.networkAliases.asJava))
      .exec()
  }

  "Matcher should free reserved balances if order wasn't placed into the queue" in {

    val initialWavesBalance: Double = denormalizeWavesAmount(wavesNode1.api.balance(alice, Waves)).toDouble
    val initialUsdBalance: Double   = denormalizeAmountAndFee(wavesNode1.api.balance(alice, usd), 2).toDouble

    val wsac = mkWsAuthenticatedConnection(alice, dex1)

    assertChanges(wsac, squash = false) { Map(Waves -> WsBalances(initialWavesBalance, 0), usd -> WsBalances(initialUsdBalance, 0)) }()

    val sellOrder    = mkOrderDP(alice, wavesUsdPair, SELL, 10.waves, 3.0)
    val bigSellOrder = mkOrderDP(alice, wavesUsdPair, SELL, 30.waves, 3.0)

    placeAndAwaitAtDex(sellOrder)

    dex1.api.currentOffset shouldBe 0
    dex1.api.reservedBalance(alice) should matchTo { Map[Asset, Long](Waves -> 10.003.waves) }

    assertChanges(wsac) { Map(Waves -> WsBalances(initialWavesBalance - 10.003, 10.003)) } {
      WsOrder.fromDomain(LimitOrder(sellOrder), OrderStatus.Accepted)
    }

    disconnectKafkaFromNetwork()

    dex1.api.tryPlace(bigSellOrder)
    dex1.api.reservedBalance(alice) should matchTo(Map[Asset, Long](Waves -> 10.003.waves))

    assertChanges(wsac, squash = false)(
      Map(Waves -> WsBalances(initialWavesBalance - 40.006, 40.006)),
      Map(Waves -> WsBalances(initialWavesBalance - 10.003, 10.003)),
    )()

    connectKafkaToNetwork()

    placeAndAwaitAtDex(bigSellOrder)
    dex1.api.reservedBalance(alice) should matchTo(Map[Asset, Long](Waves -> 40.006.waves))

    assertChanges(wsac, squash = false) { Map(Waves -> WsBalances(initialWavesBalance - 40.006, 40.006)) } {
      WsOrder.fromDomain(LimitOrder(bigSellOrder), OrderStatus.Accepted)
    }

    dex1.api.cancelAll(alice)
    wsac.close()
  }
}
