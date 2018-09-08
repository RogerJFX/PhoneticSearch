package de.crazything.search.ext

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors}

import de.crazything.search.entity.{MappedResults, PkDataSet, QueryCriteria, SearchResult}
import de.crazything.search.ext.RunnableHandlers.MapperFutureHandler
import de.crazything.search.utils.FutureUtil
import de.crazything.search.{AbstractTypeFactory, CommonSearcher, DirectoryContainer, MagicSettings}
import org.apache.lucene.search.IndexSearcher
import play.api.libs.json.OFormat

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future, Promise, TimeoutException}
import scala.util.{Failure, Success}

/**
  * What MappingSearcher does:
  * 1. We have a initial search result.
  * 2. Now we want to ask another searcher to associate his results to our initial result.
  * 3. So we have a tree of results, all scored by Lucene.
  * 3. We than may iterate over the those results to decide lately what to do with them.
  *
  * A MappedResult consists of one primary search result and a sequence of other results associated to the primary result.
  */
object MappingSearcher extends MagicSettings {

  import scala.concurrent.ExecutionContext.Implicits.global

  private def processSecondLevel[I1, I2, T1 <: PkDataSet[I1], T2 <: PkDataSet[I2]]
  (primaryResult: Seq[SearchResult[I1, T1]],
   mapperClass: (Seq[SearchResult[I1, T1]]) => Combine[I1, I2, T1, T2],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT,
   promise: Promise[Seq[MappedResults[I1, I2, T1, T2]]]): Unit = {

    val mappingClass: Combine[I1, I2, T1, T2] = mapperClass(primaryResult)
    val finalResultFuture = FutureUtil.futureWithTimeout(mappingClass.createFuture(), secondLevelTimeout)

    finalResultFuture.onComplete {
      case Success(finalResult) =>
        if(finalResult.isEmpty) { //TODO: this never should happen. We tmp. did it because some NullPointer. See below.
          promise.success(Seq.empty)
        } else {
          //TODO: we got a NullPointerException here with bigger data. What should become null here?
          promise.success(finalResult.sortBy(res => -res.target.score))
        }
      case Failure(t: TimeoutException) =>
        mappingClass.onTimeoutException(t)
        promise.failure(t)
      case Failure(x) => promise.failure(x)
    }

  }

  private def processFirstLevel[I1, I2, T1 <: PkDataSet[I1], T2 <: PkDataSet[I2]]
  (primaryResultFuture: Future[Seq[SearchResult[I1, T1]]],
   mapperClass: (Seq[SearchResult[I1, T1]]) => Combine[I1, I2, T1, T2],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT): Future[Seq[MappedResults[I1, I2, T1, T2]]] = {
    val promise: Promise[Seq[MappedResults[I1, I2, T1, T2]]] = Promise[Seq[MappedResults[I1, I2, T1, T2]]]
    primaryResultFuture.onComplete {
      case Success(res) =>
        processSecondLevel(res, mapperClass, secondLevelTimeout, promise)
      case Failure(t) => promise.failure(t)
    }
    promise.future
  }

  def search[I1, I2, T1 <: PkDataSet[I1], T2 <: PkDataSet[I2]]
  (input: T1,
   factory: AbstractTypeFactory[I1, T1],
   searcherOption: Option[IndexSearcher] = DirectoryContainer.defaultSearcher,
   queryCriteria: Option[QueryCriteria] = None,
   maxHits: Int = MAGIC_NUM_DEFAULT_HITS_FILTERED,
   mapperFn: (SearchResult[I1, T1]) => Future[Seq[SearchResult[I2, T2]]],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT)
  : Future[Seq[MappedResults[I1, I2, T1, T2]]] = {
    def secondLevelClass(res: Seq[SearchResult[I1, T1]]): Combine[I1, I2, T1, T2] = new MapperAsyncFuture(res, mapperFn)

    val searchResult: Future[Seq[SearchResult[I1, T1]]] =
      CommonSearcher.searchAsync(input, factory, queryCriteria, maxHits, searcherOption)
    processFirstLevel(searchResult, secondLevelClass, secondLevelTimeout)
  }

  def searchFuture[I1, I2, T1 <: PkDataSet[I1], T2 <: PkDataSet[I2]]
  (initialFuture: Future[Seq[SearchResult[I1, T1]]],
   mapperFn: (SearchResult[I1, T1]) => Future[Seq[SearchResult[I2, T2]]],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT)
  : Future[Seq[MappedResults[I1, I2, T1, T2]]] = {
    def secondLevelClass(res: Seq[SearchResult[I1, T1]]): Combine[I1, I2, T1, T2] = new MapperAsyncFuture(res, mapperFn)

    processFirstLevel(initialFuture, secondLevelClass, secondLevelTimeout)
  }

  def searchRemote[I1, I2, T1 <: PkDataSet[I1], T2 <: PkDataSet[I2]]
  (input: T1,
   url: String,
   firstLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT,
   mapperFn: (SearchResult[I1, T1]) => Future[Seq[SearchResult[I2, T2]]],
   secondLevelTimeout: FiniteDuration = MAGIC_DEFAULT_TIMEOUT)
  (implicit fmt: OFormat[T1])
  : Future[Seq[MappedResults[I1, I2, T1, T2]]] = {
    def secondLevelClass(res: Seq[SearchResult[I1, T1]]): Combine[I1, I2, T1, T2] = new MapperAsyncFuture(res, mapperFn)

    val searchResult: Future[Seq[SearchResult[I1, T1]]] =
      CommonSearcher.searchRemote[I1, T1](input, url, firstLevelTimeout)
    processFirstLevel(searchResult, secondLevelClass, secondLevelTimeout)
  }

  val processors: Int = Runtime.getRuntime.availableProcessors()

  private trait Combine[I1, I2, T1 <: PkDataSet[I1], T2 <: PkDataSet[I2]] {
    // Yes, we can do this here. We take care of the pool in our createFuture methods.
    //val processors: Int
    val pool: ExecutorService = Executors.newFixedThreadPool(processors)
    val ec: ExecutionContext = ExecutionContext.fromExecutorService(pool)
    val promise: Promise[Seq[MappedResults[I1, I2, T1, T2]]] = Promise[Seq[MappedResults[I1, I2, T1, T2]]]
    val buffer: ListBuffer[MappedResults[I1, I2, T1, T2]] = ListBuffer[MappedResults[I1, I2, T1, T2]]()
    val counter = new AtomicInteger()
    val procCount = new AtomicInteger()

    def onTimeoutException(exc: Exception): Unit = {
      pool.shutdownNow()
    }

    def onCombineException(exc: Throwable): Unit = {
      if (!promise.isCompleted) {
        promise.failure(exc)
        pool.shutdownNow()
      }
    }

    def createFuture(): Future[Seq[MappedResults[I1, I2, T1, T2]]]

    protected def doCreateFuture(raw: Seq[SearchResult[I1, T1]],
                                 body: (Int) => Unit): Future[Seq[MappedResults[I1, I2, T1, T2]]] = {
      body(raw.length)
      promise.future
    }
  }

  private class MapperAsyncFuture[I1, I2, T1 <: PkDataSet[I1], T2 <: PkDataSet[I2]]
  (raw: Seq[SearchResult[I1, T1]],
   mappingFn: (SearchResult[I1, T1]) => Future[Seq[SearchResult[I2, T2]]])
    extends Combine[I1, I2, T1, T2] {
    override def createFuture(): Future[Seq[MappedResults[I1, I2, T1, T2]]] = {
      doCreateFuture(raw, (len: Int) => {
        def checkLenInc(): Unit =
          this.synchronized { //TODO: really?
            if (counter.incrementAndGet() == len) {
              promise.success(buffer)
              pool.shutdown()
            } else if (procCount.get() < len) {
              pool.execute(new MapperFutureHandler(mappingFn, raw(procCount.getAndIncrement()),
                buffer, () => checkLenInc(), onCombineException)(ec))
            }
          }
        val shorter = if (processors < len) processors else len
        for (i <- 0 until shorter) {
          procCount.incrementAndGet()
          pool.execute(new MapperFutureHandler(mappingFn, raw(i), buffer, () => checkLenInc(), onCombineException)(ec))
        }
      })
    }

    //override val processors: Int = 4
  }

}
