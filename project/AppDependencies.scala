import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.18.0"
  private val scalaCheckVersion = "1.17.1"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "org.scalacheck"          %% "scalacheck"                 % scalaCheckVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
    "org.scalacheck"          %% "scalacheck"                 % scalaCheckVersion           % Test,
    "org.scalatestplus"       %% "scalacheck-1-17"            % "3.2.17.0"                  % Test
  )

  val it: Seq[Nothing] = Seq.empty
}
