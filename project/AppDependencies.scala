import sbt._

object AppDependencies {

  private val bootstrapVersion     = "10.5.0"
  private val hmrcMongoVersion     = "2.12.0"
  private val openHtmlToPdfVersion = "1.1.31"

  val compile = Seq(
    "uk.gov.hmrc"            %% "bootstrap-backend-play-30"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-play-30"                % hmrcMongoVersion,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-work-item-repo-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"            %% "agent-mtd-identifiers"             % "3.0.0",
    "uk.gov.hmrc"            %% "crypto-json-play-30"               % "8.4.0",
    "uk.gov.hmrc"            %% "internal-auth-client-play-30"      % "4.1.0",
    "io.github.openhtmltopdf" % "openhtmltopdf-pdfbox"              % openHtmlToPdfVersion,
  )

  val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test,
    "org.scalamock"     %% "scalamock"               % "7.5.0"          % Test,
  )

  val it = Seq.empty
}
