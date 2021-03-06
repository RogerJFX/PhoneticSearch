package de.crazything.app.itest

import de.crazything.app._
import de.crazything.app.analyze.NoLanguage
import de.crazything.app.entity.{Person, SkilledPerson, SocialPerson}
import de.crazything.app.factory.SkilledPersonFactory
import de.crazything.app.helpers.DataProvider
import de.crazything.search.entity.{MappedResults, MappedResultsCollection, SearchResult}
import de.crazything.search.ext.MappingSearcher
import de.crazything.search.{CommonIndexer, CommonSearcher, DirectoryContainer}
import de.crazything.service.{QuickJsonParser, RestClient}
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll}

import scala.concurrent.Future
import scala.concurrent.duration._

class DockerTest2 extends AsyncFlatSpec with BeforeAndAfterAll with QuickJsonParser with NoLanguage with DirectoryContainer with Network {

  val standardPerson = Person(-1, "Herr", "firstName", "lastName", "street", "city")


  override def beforeAll(): Unit = {
    CommonIndexer.index(DataProvider.readSkilledPersons(), SkilledPersonFactory, "skilledIndex")
  }

  "Scala dude" should "should be found" in {
    val searchedSkilledPerson = SkilledPerson(-1, None, None, Some(Seq("Scala", "Postgresql")))
    CommonSearcher.searchAsync(searchedSkilledPerson, SkilledPersonFactory, searcherOption = "skilledIndex").map(result => {
      println(result)
      assert(result.length == 1)
      assert(result.head.found.firstName.get == "Roger")
    })
  }

  "Cascaded search" should "work using custom case class" in {

    val searchedSkilledPerson = SkilledPerson(-1, None, None, Some(Seq("Scala", "Postgresql")))

    def combineBaseAndSocialData(skilledPerson: SearchResult[Int, SkilledPerson]): Future[Seq[SearchResult[Int, PersonWithSocialResults]]] = {
      val searchedBasePerson: Person = Person(-1, "", skilledPerson.found.firstName.getOrElse("-"),
        skilledPerson.found.lastName.getOrElse("-"), "", "")
      val restResponse: Future[PersonWithSocialPersonsCollection] =
        RestClient.post[Person, PersonWithSocialPersonsCollection](urlFromUriBase("findBaseDataForWithSocial"), searchedBasePerson)
      restResponse.map(res => {
        res.found.map((rr: PersonWithSocialResults) => SearchResult[Int, PersonWithSocialResults](rr, rr.person.score))
      })
    }

    MappingSearcher.search(input = searchedSkilledPerson, factory = SkilledPersonFactory,
      searcherOption = "skilledIndex",
      mapperFn = combineBaseAndSocialData, secondLevelTimeout = 15.seconds)
      .map((result: Seq[MappedResults[Int, Int, SkilledPerson, PersonWithSocialResults]]) => {
        println(result)
        assert(result.head.results.head.found.socialResults.length == 2)
        assert(result.length == 1)
      })
  }

  it should "even work with generic DTO class" in {

    val searchedSkilledPerson = SkilledPerson(-1, None, None, Some(Seq("Scala", "Postgresql")))

    def combineBaseAndSocialData(skilledPerson: SearchResult[Int, SkilledPerson]):
    Future[Seq[SearchResult[Int, MappedResults[Int, Int, Person, SocialPerson]]]] = {
      val searchedBasePerson: Person = Person(-1, "", skilledPerson.found.firstName.getOrElse("-"),
        skilledPerson.found.lastName.getOrElse("-"), "", "")
      val restResponse: Future[MappedResultsCollection[Int, Int, Person, SocialPerson]] =
        RestClient.post[Person, MappedResultsCollection[Int, Int, Person, SocialPerson]](
          urlFromUriBase("mapSocial2Base"), searchedBasePerson)
      val result: Future[Seq[SearchResult[Int, MappedResults[Int, Int, Person, SocialPerson]]]] =
        restResponse.map(res => {
          res.entries.map(rr => SearchResult[Int, MappedResults[Int, Int, Person, SocialPerson]](rr, rr.target.score))
        })
      result
    }


    MappingSearcher.search(input = searchedSkilledPerson, factory = SkilledPersonFactory,
      searcherOption = "skilledIndex",
      mapperFn = combineBaseAndSocialData, secondLevelTimeout = 15.seconds)
      .map((result: Seq[MappedResults[Int, Int, SkilledPerson, MappedResults[Int, Int, Person, SocialPerson]]]) => {
        println(result)
        assert(result.head.results.head.found.results.length == 2)
        assert(result.length == 1)
      })
  }
}
