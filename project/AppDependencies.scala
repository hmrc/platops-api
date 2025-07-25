import sbt._

object AppDependencies {

  val hmrcMongoVersion = "2.6.0"
  val bootstrapVersion = "9.19.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-work-item-repo-play-30" % hmrcMongoVersion,
    "org.typelevel"           %% "cats-core"                         % "2.13.0",
    "commons-codec"           %  "commons-codec"                     % "1.15"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion    % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion    % Test,
    "org.mongodb.scala"       %% "mongo-scala-driver"         % "5.1.0" cross CrossVersion.for3Use2_13
  )
}
