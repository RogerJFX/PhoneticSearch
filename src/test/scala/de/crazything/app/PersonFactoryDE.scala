package de.crazything.app

import java.util.concurrent.atomic.AtomicReference

import de.crazything.search._
import de.crazything.search.entity.{PkDataSet, QueryCriteria}
import org.apache.lucene.document._
import org.apache.lucene.search._
import org.slf4j.LoggerFactory

object PersonFactoryDE extends AbstractTypeFactory[Int, Person] with PersonQueries {

  private val logger = LoggerFactory.getLogger(PersonFactoryDE.getClass)

  private[app] val PK = "id"

  private[app] val SALUTATION = "salutation"
  private[app] val FIRST_NAME = "firstName"
  private[app] val LAST_NAME = "lastName"
  private[app] val STREET = "street"
  private[app] val CITY = "city"

  val customEnabledQuery_Name = "customEnabledQuery_lastName"
  val customQuery_FirstAndLastName = "customQuery_FirstAndLastName"

  override def createInstanceFromDocument(doc: Document): PkDataSet[Int] = {
    DataContainer.findById(doc.get(PK).toInt)
  }

  override def setDataPool(data: Seq[Person]): Unit = {
    DataContainer.setData(data)
  }

  override def populateDocument(document: Document, person: Person): Unit = {

    addPkField(document, PK, person.id)

    addField(document, SALUTATION, person.salutation)
    addField(document, FIRST_NAME, person.firstName)
    addField(document, LAST_NAME, person.lastName)
    addField(document, STREET, person.street)
    addField(document, CITY, person.city)

  }

  override def createQuery(person: Person): Query = doCreateStandardQuery(person)

  override val selectQueryCreator:(QueryCriteria, Person) => Query = (criteria, person) => {
    criteria.queryName match {
      case `customEnabledQuery_Name` => createSuperCustomQuery(person, criteria.queryEnableOpt)
      case `customQuery_FirstAndLastName` => createFirstAndLastNameQuery(person)
      case _ =>
        logger.warn("No matching query name found. Falling back to standard `createQuery`")
        createQuery(person)
    }

  }

  object DataContainer {

    case class Data(data: Seq[Person]) {
      def findById(id: Int): Option[Person] = data.find(d => d.id == id)
    }

    private val dataRef: AtomicReference[Data] = new AtomicReference[Data]()

    def setData(data: Seq[Person]): Unit = {
      dataRef.set(Data(data))
    }

    def findById(id: Int): Person = {
      dataRef.get().findById(id).get
    }

  }

}
