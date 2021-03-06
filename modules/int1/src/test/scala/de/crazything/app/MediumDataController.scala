package de.crazything.app

import de.crazything.app.analyze.GermanLanguage
import de.crazything.app.entity.{Person, SocialPerson}
import de.crazything.app.factory.PersonFactoryDE
import de.crazything.app.helpers.DataProvider
import de.crazything.app.itest.Network
import de.crazything.search.entity.{QueryCriteria, SearchResult, SearchResultCollection}
import de.crazything.search.{AbstractTypeFactory, CommonIndexer}
import de.crazything.service.RestClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MediumDataController extends AbstractDataController with Network with GermanLanguage {

  override protected def combineFacebookScored(basePerson: SearchResult[Int, Person]): Future[Seq[SearchResult[Int, SocialPerson]]] = {
    val restResponse: Future[SearchResultCollection[Int, SocialPerson]] =
      RestClient.post[Person, SearchResultCollection[Int, SocialPerson]](urlFromUriSocial("findSocialForScoredBig"),
        basePerson.found)
    restResponse.map(res => res.entries)
  }

  override protected val personFactory: AbstractTypeFactory[Int, Person] = new PersonFactoryDE()

  override protected val searchDirectoryName = "bigData"

  override protected val queryCriteria: Option[QueryCriteria] =
    Some(QueryCriteria(PersonFactoryDE.customQuery_FirstAndLastName, None))

  CommonIndexer.index(DataProvider.readVerySimplePersonsResourceBig(), personFactory, searchDirectoryName)
}
