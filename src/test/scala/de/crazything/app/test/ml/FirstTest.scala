package de.crazything.app.test.ml

import java.net.URL
import java.nio.file.{Files, Path, Paths}

import de.crazything.app.analyze.NoLanguage
import de.crazything.app.helpers.DataProvider
import de.crazything.search.entity.SearchResult
import de.crazything.search.ml.tuning.{SimpleTuner, TunerConfig}
import de.crazything.search.{CommonIndexer, CommonSearcher, QueryConfig}
import de.crazything.service.QuickJsonParser
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Random

class FirstTest extends FlatSpec with Matchers with QueryConfig with NoLanguage with QuickJsonParser{

  val preferredId = 4716

  val standardSlogan = Slogan(-1, "Roger", "Francis", "*innovate*", "*embrace*", "*systems*")

  val tunerConfig: TunerConfig = {
    import scala.collection.JavaConverters._
    val url: URL = this.getClass.getResource("/conf/simpleSlogan.tuning.json")
    val path: Path = Paths.get(url.toURI)
    val lines: Seq[String] = Files.readAllLines(path).asScala
    val complete = lines.mkString("\n")
    jsonString2T[TunerConfig](complete)
  }

  val sloganFactory = new SloganFactory(new SimpleTuner(tunerConfig))

  CommonIndexer.index(DataProvider.readSlogans(), sloganFactory)

  def findPosition(searchResult: Seq[SearchResult[Int, Slogan]]): Int = searchResult.indexWhere(s => s.found.id == preferredId)

  private def search() = CommonSearcher.search(standardSlogan.copy(slogan1 = "*sexy*"), sloganFactory, maxHits = 1000)

  private val random = new Random()

  private def randomIp(): String = s"${random.nextInt(255)}.${random.nextInt(255)}.${random.nextInt(255)}.${random.nextInt(255)}"


  "Boost" should "promote a result due to users' acceptance" in {

    val initialSearchResult = search()
    val initialPosition = findPosition(initialSearchResult)
    assert(initialPosition == 54)

    for (_ <- 0 to 200) {
      val searchResult = search()
      val position = findPosition(searchResult)

      sloganFactory.notifyFeedback(randomIp(), position,1)
    }

    val searchResult = search()
    val position = findPosition(searchResult)
    println(s"Position: $position")
    assert(position <= 12)

  }

  it should "do nothing with only one ip" in {
    sloganFactory.resetTuning()
    val initialSearchResult = search()
    val initialPosition = findPosition(initialSearchResult)
    assert(initialPosition == 54)

    for (_ <- 0 to 200) {
      val searchResult = search()
      val position = findPosition(searchResult)
      sloganFactory.notifyFeedback("192.169.0.1", position,1)
    }

    val searchResult = search()
    val position = findPosition(searchResult)
    println(s"Position: $position")
    assert(position > 12)

  }
}
