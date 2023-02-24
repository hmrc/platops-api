import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

lazy val microservice = Project("platops-api", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion             :=  0,
    scalaVersion             :=  "2.13.10",
    PlayKeys.playDefaultPort :=  8860,
    libraryDependencies      ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions            +=  "-Wconf:src=routes/.*:s"

  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
