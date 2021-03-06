package com.wavesplatform.dex.it.test

import java.time.{LocalDateTime, ZoneId}

import com.wavesplatform.dex.it.api.BaseContainersKit
import mouse.any._
import org.scalatest.{Args, Status, Suite}

import scala.util.{Failure, Success}

trait InformativeTestStart extends Suite { self: BaseContainersKit =>

  override protected def runTest(testName: String, args: Args): Status = {

    def print(text: String): Unit = {
      val formatted = s"---------- [${LocalDateTime.now(ZoneId.of("UTC"))}] $text ----------"
      log.debug(formatted)
      knownContainers.get().foreach { _.printDebugMessage(formatted) }
    }

    print(s"Test '$testName' started")

    super.runTest(testName, args) unsafeTap {
      _.whenCompleted {
        case Success(r) => print(s"Test '$testName' ${if (r) "succeeded" else "failed"}")
        case Failure(e) => print(s"Test '$testName' failed with exception '${e.getClass.getSimpleName}'")
      }
    }
  }
}
