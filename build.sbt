import sbt.Keys.scalacOptions
import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.6.1"

val appName = "agent-services-account"

lazy val microservice = Project("agent-services-account", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    PlayKeys.playDefaultPort := 9402,
    name := appName,
    organization := "uk.gov.hmrc",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    routesImport ++= Seq(
      "uk.gov.hmrc.agentservicesaccount.binders.PathBinders.*",
      "uk.gov.hmrc.agentservicesaccount.binders.QueryBinders.*",
      "uk.gov.hmrc.agentservicesaccount.models.subscription.LegacyRegime",
      "uk.gov.hmrc.agentmtdidentifiers.model.Arn"
    ),
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
//    Test / parallelExecution := false,
    scalacOptions += "-Wconf:src=routes/.*:s"
  )
  .settings(Test / logBuffered := false)
  .settings(CodeCoverageSettings.settings)
  .disablePlugins(JUnitXmlReportPlugin)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)
  .settings(Test / logBuffered := false)
