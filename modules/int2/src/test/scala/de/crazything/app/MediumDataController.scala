package de.crazything.app

import de.crazything.app.test.helpers.DataProvider
import de.crazything.search.{AbstractTypeFactory, CommonIndexer}

object MediumDataController extends AbstractDataController with GermanLanguage {

  override protected def socialPersonFactory: AbstractTypeFactory[Int, SocialPerson] = new SocialPersonFactory()

  CommonIndexer.index(DataProvider.readSocialPersonsResourceBig(), socialPersonFactory, "bigData")

}