package de.crazything.search

import de.crazything.search.entity.{PkDataSet, QueryCriteria, SearchResult, SearchResultCollection}
import de.crazything.search.utils.FutureUtil
import de.crazything.service.RestClient
import org.apache.lucene.search.{IndexSearcher, Query, ScoreDoc}
import play.api.libs.json.OFormat

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

object CommonSearcher extends MagicSettings{

  def search[I, T <: PkDataSet[I]](input: T,
                                   factory: AbstractTypeFactory[I, T],
                                   queryCriteria: Option[QueryCriteria] = None,
                                   maxHits: Int = MAGIC_NUM_DEFAULT_HITS,
                                   offset: Int = 0,
                                   searcherOption: Option[IndexSearcher] = DirectoryContainer.defaultSearcher): Seq[SearchResult[I, T]] = {
    searcherOption match {
      case Some(searcher) =>
        val query: Query =
          queryCriteria match {
            case None => factory.createQuery(input)
            case Some(qeOpt) => factory.selectQueryCreator(qeOpt, input)
          }

        val hits: Array[ScoreDoc] = searcher.search(query, maxHits + offset).scoreDocs

        val buffer = ListBuffer[SearchResult[I, T]]()

        hits.drop(offset).foreach(hit => {
          val hitOpt: Option[T] = factory.createInstanceFromDocument(searcher.doc(hit.doc))
          hitOpt match {
            case Some(entry: T) => buffer.append(SearchResult(entry, hit.score))
            case _ =>
          }
        })

        buffer

      case None => throw new IllegalStateException("Nobody told us to have a directory reference. No yet finished? " +
        "Anything async? We should fix this then")
    }

  }

  def searchAsync[I, T <: PkDataSet[I]](input: T,
                                        factory: AbstractTypeFactory[I, T],
                                        queryCriteria: Option[QueryCriteria] = None,
                                        maxHits: Int = MAGIC_NUM_DEFAULT_HITS,
                                        offset: Int = 0,
                                        searcherOption: Option[IndexSearcher] = DirectoryContainer.defaultSearcher)
                                       (implicit ec: ExecutionContext): Future[Seq[SearchResult[I, T]]] = Future {
    search(input, factory, queryCriteria, maxHits, offset, searcherOption)
  }

  def searchRemote[I, T <: PkDataSet[I]](input: T,
                                         url: String,
                                         timeout: FiniteDuration = MAGIC_ONE_DAY)
                                        (implicit fmt: OFormat[T],
                                         rmt: OFormat[SearchResultCollection[I, T]],
                                         ec: ExecutionContext): Future[Seq[SearchResult[I, T]]] = {
    val postFuture: Future[SearchResultCollection[I, T]] = RestClient.post[T, SearchResultCollection[I, T]](url, input)
    val timingOutFuture = FutureUtil.futureWithTimeout(postFuture, timeout)
    timingOutFuture.map(r => r.entries)

  }

}
