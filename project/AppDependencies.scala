import sbt.*

object AppDependencies {

  private val playVer: String = "play-30"
  private val bootstrapVersion: String = "10.8.0"
  private val hmrcMongoVersion: String = "2.12.0"
  private val pekkoVer: String = "1.0.3"
  private val openHtmlToPdfVersion: String = "1.1.37"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-backend-$playVer"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-work-item-repo-$playVer" % hmrcMongoVersion,
    "uk.gov.hmrc"            %% s"agent-mtd-identifiers"              % "3.0.0",
    "uk.gov.hmrc"            %% s"crypto-json-$playVer"               % "8.4.0",
    "uk.gov.hmrc"            %% s"internal-auth-client-$playVer"      % "4.4.0",
    "io.github.openhtmltopdf" % "openhtmltopdf-pdfbox"                % openHtmlToPdfVersion,
    "io.github.samueleresca" %% "pekko-quartz-scheduler"              % "1.2.2-pekko-1.0.x"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVer"  % bootstrapVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVer" % hmrcMongoVersion,
    "org.apache.pekko"  %% "pekko-actor-testkit-typed" % pekkoVer,
    "org.scalamock"     %% "scalamock"                 % "7.5.5"
  ).map(_ % Test)

  val it: Seq[Nothing] = Seq.empty
}