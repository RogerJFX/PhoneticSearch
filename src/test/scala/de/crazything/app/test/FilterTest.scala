package de.crazything.app.test

import de.crazything.app.factory.PersonFactoryDE
import de.crazything.app.helpers.DataProvider
import de.crazything.app.analyze.GermanLanguage
import de.crazything.app.entity.Person
import de.crazything.search.entity.SearchResult
import de.crazything.search.ext.FilteringSearcher
import de.crazything.search.{CommonIndexer, QueryConfig}
import org.scalatest.FlatSpec

class FilterTest extends FlatSpec with QueryConfig with GermanLanguage {

  private def filterFrankfurt(result: SearchResult[Int, Person]): Boolean = result.found.city.contains("Frankfurt")

  val standardPerson = Person(-1, "Herr", "firstName", "lastName", "street", "city")

  CommonIndexer.index(DataProvider.readVerySimplePersons(), PersonFactoryDE)

  "Filter" should "exclude Mayer living not in Frankfurt" in {
    val searchResult =
      FilteringSearcher.simpleSearch(standardPerson.copy(lastName = "Mayer"), PersonFactoryDE, filterFn = filterFrankfurt)
    assert(searchResult.isEmpty)
  }

  it should "pass Hösl living in Frankfurt" in {
    val searchResult =
      FilteringSearcher.simpleSearch(standardPerson.copy(lastName = "Hösl"), PersonFactoryDE, filterFn = filterFrankfurt)
    assert(searchResult.length == 1)
  }

}
