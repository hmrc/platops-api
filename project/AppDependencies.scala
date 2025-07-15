import sbt._

object AppDependencies {

  val bootstrapVersion = "9.16.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "org.typelevel"           %% "cats-core"                  % "2.10.0",
    "commons-codec"           %  "commons-codec"              % "1.15"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion    % Test,
    "org.mongodb.scala"       %% "mongo-scala-driver"         % "5.1.0" cross CrossVersion.for3Use2_13
  )
}
