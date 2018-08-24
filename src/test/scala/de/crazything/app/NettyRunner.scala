package de.crazything.app

import java.io.File
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import de.crazything.search.{CommonSearcher, DirectoryContainer}
import de.crazything.search.entity.{MappedResults, MappedResultsCollection, SearchResult, SearchResultCollection}
import de.crazything.search.ext.MappingSearcher
import de.crazything.service.{EmbeddedRestServer, QuickJsonParser, RestClient}
import play.api.Mode
import play.api.libs.json.JsValue
import play.api.mvc.{Action, Results}
import play.api.routing.Router
import play.api.routing.sird._
import play.core.server.{NettyServer, ServerConfig}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object NettyRunner extends QuickJsonParser{

  val serverRef: AtomicReference[NettyServer] = new AtomicReference[NettyServer]()
  val testRunCalls: AtomicInteger = new AtomicInteger()

  val serverConfig = ServerConfig(
    this.getClass.getClassLoader,
    new File("."),
    Some(0),
    None,
    "127.0.0.1",
    Mode.Test,
    System.getProperties)

  val router: Router = Router.from { // No, Router.from is not deprecated, but Tags above "from".
    case GET(p"/foo") => Action {
      Results.Created("""{"id":1,"salutation":"Herr","firstName":"firstName","lastName":"lastName","street":"street","city":"city"}""")
        .as("application/json")
    }
    case POST(p"/qux") => Action {
      request => {
        val body: JsValue = request.body.asJson.get
        Results.Created(body).as("application/json")
      }
    }
//    case POST(p"/findSocialFor") => Action {
//      request => {
//        val person: Person = jsonString2T[Person](request.body.asJson.get.toString())
//        val socialPerson: SocialPerson = SocialPerson(-1, person.firstName, person.lastName)
//        val searchResult: Seq[SearchResult[Int, SocialPerson]] =
//          CommonSearcher.search(input = socialPerson, factory = SocialPersonFactory, searcherOption = DirectoryContainer.pickSearcher("remoteIndex"))
//        val strSearchResult: String = t2JsonString[SocialPersonCollection](SocialPersonCollection(searchResult.map(r => r.obj)))
//        Results.Created(strSearchResult).as("application/json")
//      }
//    }
    case POST(p"/findSocialForScored") => Action {
      request => {
        val person: Person = jsonString2T[Person](request.body.asJson.get.toString())
        val socialPerson: SocialPerson = SocialPerson(-1, person.firstName, person.lastName)
        val searchResult: Seq[SearchResult[Int, SocialPerson]] =
          CommonSearcher.search(input = socialPerson, factory = SocialPersonFactory, searcherOption = DirectoryContainer.pickSearcher("remoteIndex"))

        val strSearchResult: String = t2JsonString[SearchResultCollection[Int, SocialPerson]](SearchResultCollection(searchResult))
        Results.Created(strSearchResult).as("application/json")
      }
    }
    case POST(p"/mapSocial2Base") => Action.async {
      request => {
        val person: Person = jsonString2T[Person](request.body.asJson.get.toString())
        MappingSearcher.searchMapping(input = person, factory = PersonFactoryDE,
          mapperFn = combineFacebookScored, secondLevelTimeout = 5.seconds).map((searchResult: Seq[MappedResults[Int, Int, Person, SocialPerson]]) => {
          val strSearchResult: String = t2JsonString[MappedResultsCollection[Int, Int, Person, SocialPerson]](MappedResultsCollection(searchResult))
          Results.Created(strSearchResult).as("application/json")
        })
      }
    }
    case POST(p"/findSkilledPerson") => Action {
      request => {
        val person: SkilledPerson = jsonString2T[SkilledPerson](request.body.asJson.get.toString())
        val searchResult: Seq[SearchResult[Int, SkilledPerson]] =
          CommonSearcher.search(input = person, factory = SkilledPersonFactory, searcherOption = DirectoryContainer.pickSearcher("skilledIndex"))
        val strSearchResult: String = t2JsonString[SearchResultCollection[Int, SkilledPerson]](SearchResultCollection(searchResult))
        Results.Created(strSearchResult).as("application/json")
      }
    }
    case _ => Action {
      Results.NotFound
    }
  }

  def runServer: NettyServer = {
    this.synchronized {
      if (testRunCalls.getAndIncrement() == 0) {
        val runningServer = EmbeddedRestServer.run(serverConfig, router)
        serverRef.set(runningServer)
        port = runningServer.httpPort.get
        runningServer
      } else {
        serverRef.get()
      }
    }
  }

  def stopServer(): Unit = {
    this.synchronized {
      if (testRunCalls.decrementAndGet() == 0) {
        serverRef.get().stop()
      }
    }
  }

  var port = 0

  def urlFromUri(uri: String): String = s"http://127.0.0.1:$port/$uri"

  def combineFacebookScored(basePerson: SearchResult[Int, Person]): Future[Seq[SearchResult[Int, SocialPerson]]] = {
    val restResponse: Future[SearchResultCollection[Int, SocialPerson]] =
      RestClient.post[Person, SearchResultCollection[Int, SocialPerson]](urlFromUri("findSocialForScored"), basePerson.obj)
    restResponse.map(res => res.entries)
  }
}
