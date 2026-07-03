import sbt.*

object AppDependencies {

  private val bootstrapVersion     = "10.8.0"
  private val hmrcMongoVersion     = "2.12.0"
  private val openHtmlToPdfVersion = "1.1.37"
  private val pekkoVer = "1.0.3"
  private val playVer: String = "play-30"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"            %% s"bootstrap-backend-$playVer"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% s"hmrc-mongo-work-item-repo-$playVer" % hmrcMongoVersion,
    "uk.gov.hmrc"            %% s"agent-mtd-identifiers"             % "3.0.0",
    "uk.gov.hmrc"            %% s"crypto-json-$playVer"               % "8.4.0",
    "uk.gov.hmrc"            %% s"internal-auth-client-$playVer"      % "4.4.0",
    "io.github.openhtmltopdf" % "openhtmltopdf-pdfbox"              % openHtmlToPdfVersion,
    "io.github.samueleresca" %% "pekko-quartz-scheduler"            % "1.2.2-pekko-1.0.x"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVer"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVer" % hmrcMongoVersion % Test,
    "org.apache.pekko" %% "pekko-actor-testkit-typed"% pekkoVer         % Test,
    "org.scalamock"     %% "scalamock"               % "7.5.5"          % Test,
  )

  val it: Seq[Nothing] = Seq.empty
}