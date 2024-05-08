import uk.gov.hmrc.DefaultBuildSettings
import play.sbt.routes.RoutesKeys

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.12"

lazy val microservice = Project("platops-api", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    PlayKeys.playDefaultPort :=  8860,
    libraryDependencies      ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions            +=  "-Wconf:src=routes/.*:s"

  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .settings(
    RoutesKeys.routesImport ++= Seq(
      "uk.gov.hmrc.platopsapi.models.RepoType"
    )
  )

lazy val it =
    (project in file("it"))
      .enablePlugins(PlayScala)
      .dependsOn(microservice % "test->test")
      .settings(DefaultBuildSettings.itSettings())
