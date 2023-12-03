ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/killaitis/peloton"),
    "https://github.com/killaitis/peloton.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id    = "killaitis",
    name  = "Andreas Killaitis",
    email = "andreas@killaitis.de",
    url   = url("http://www.github.com/killaitis/")
  )
)

ThisBuild / licenses             := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php"))
ThisBuild / homepage             := Some(url("https://github.com/killaitis/peloton"))
ThisBuild / organization         := "de.killaitis"
ThisBuild / organizationName     := "Andreas Killaitis"
ThisBuild / organizationHomepage := Some(url("https://www.github.com/killaitis"))

publishTo := sonatypePublishToBundle.value
publishMavenStyle := true

pomIncludeRepository := { _ => false }

sonatypeCredentialHost := "s01.oss.sonatype.org"
sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

credentials ++= (for {
  username <- sys.env.get("SONATYPE_USERNAME")
  password <- sys.env.get("SONATYPE_PASSWORD")
} yield Credentials(
  "Sonatype Nexus Repository Manager",
  "s01.oss.sonatype.org",
  username,
  password
)).toList
