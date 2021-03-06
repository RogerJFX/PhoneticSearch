package de.crazything.app.itest

import de.crazything.app._
import de.crazything.app.analyze.GermanLanguage
import de.crazything.app.entity.{Person, SocialPerson}
import de.crazything.app.factory.PersonFactoryDE
import de.crazything.app.helpers.DataProvider
import de.crazything.search.CommonIndexer
import de.crazything.search.entity.{MappedResults, SearchResult, SearchResultCollection}
import de.crazything.search.ext.MappingSearcher
import de.crazything.service.{QuickJsonParser, RestClient}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class DockerTest1 extends AsyncFlatSpec with BeforeAndAfterAll with QuickJsonParser with GermanLanguage with Network{

  val standardPerson = Person(-1, "Herr", "firstName", "lastName", "street", "city")

  override def beforeAll(): Unit = {
    CommonIndexer.index(DataProvider.readVerySimplePersons(), PersonFactoryDE)
  }

  def combineFacebookScored(result: SearchResult[Int, Person]): Future[Seq[SearchResult[Int, SocialPerson]]] = {
    val restResponse: Future[SearchResultCollection[Int, SocialPerson]] =
      RestClient.post[Person, SearchResultCollection[Int, SocialPerson]](urlFromUriSocial("findSocialForScored"), result.found)
    println(result.found)
    restResponse.andThen {
      case Success(res) => println(res)
      case Failure(t) => println(t.getMessage)
    }
    restResponse.map(res => res.entries)
  }

  "Scored remote docker" should "get a non empty score result for person having facebook account" in {
    val searchedPerson = Person(-1, "Herr", "Franz", "Reißer", "street", "city")
    MappingSearcher.search(input = searchedPerson, factory = PersonFactoryDE,
      mapperFn = combineFacebookScored, secondLevelTimeout = 5.seconds).map((result: Seq[MappedResults[Int, Int, Person, SocialPerson]]) => {
      println(result)
      assert(result.length == 1)
    })
  }
}
