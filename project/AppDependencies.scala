import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.12.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "org.scalacheck"          %% "scalacheck"                 % "1.18.1",
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
  )

  val it: Seq[Nothing] = Seq.empty
}
