import sbt._

object AppDependencies {

  val bootstrapVersion = "7.12.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % bootstrapVersion,
    "org.typelevel"           %% "cats-core"                  % "2.9.0",
    "commons-codec"           %  "commons-codec"              % "1.15",
    "uk.gov.hmrc"             %% "http-verbs"                 % "14.9.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVersion    % "test, it",
  )
}
