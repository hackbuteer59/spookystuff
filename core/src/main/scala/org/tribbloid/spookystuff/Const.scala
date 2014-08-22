package org.tribbloid.spookystuff

import com.fasterxml.jackson.databind.{ObjectMapper, ObjectWriter}
import org.tribbloid.spookystuff.factory.NaiveDriverFactory

/**
 * Created by peng on 04/06/14.
 */
//TODO: propose to merge with SpookyContext
//TODO: can use singleton pattern? those values never changes after SparkContext is defined
final object Const {

  val pageDelay = 10
  val resourceTimeout = 60
//  val usePageCache = false //delegated to smart execution
  val pageExpireAfter = 1800

  //default max number of elements scraped from a page, set to Int.max to allow unlimited fetch
  val fetchLimit = 100

  val defaultCharset = "ISO-8859-1"

  val savePagePath = "s3n://spooky-page"

  val localSavePagePath = "temp/spooky-page/"
//  val saveScreenshotPath = "file:///home/peng/spookystuffScreenShots/"

  val errorPageDumpDir = "s3n://spooky-errordump"
  val localErrorPageDumpDir = "temp/spooky-errordump"

  val defaultDriverFactory = new NaiveDriverFactory()
//  type Logging = com.typesafe.scalalogging.slf4j.Logging

//  val webClientOptions = new WebClientOptions
//  webClientOptions.setUseInsecureSSL(true)

  val driverCallTimeout = 60

  val localRetry = 3
}