/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.platopsapi

import java.io.File
import scala.io.Source
import scala.util.Using

//class ResourceUtil(private val basePath: String) {
//
//  def fromResource(resource: String): String = {
//    val resourcePath = s"$basePath/$resource"
//    Option(new File(System.getProperty("user.dir"), resourcePath))
//      .fold(
//        sys.error(s"Could not find resource at $resourcePath")
//      )(
//        resource =>
//          Using(Source.fromFile(resource)) { source =>
//            source.mkString
//          }.getOrElse {
//            sys.error(s"Error reading resource from $resourcePath")
//          }
//      )
//  }
//}

object ResourceUtil {
  //def apply(basePath: String): ResourceUtil = new ResourceUtil(basePath)
  def fromResource(resourceName: String): String =
    Option(getClass.getResourceAsStream(resourceName))
      .fold(
         sys.error(s"Could not find resource $resourceName on classpath")
       )(resource => Source.fromInputStream(resource).mkString)
}
