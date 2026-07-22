import sbt.*

object Publish:
  lazy val commonPublishSettings = Seq(
    organization         := "de.killaitis",
    organizationName     := "Andreas Killaitis",
    organizationHomepage := Some(url("https://www.github.com/killaitis")),
    
    publishTo := {
      val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
      if (version.value.endsWith("-SNAPSHOT"))
        Some("central-snapshots" at centralSnapshots)
      else
        localStaging.value
    },

    licenses := Seq("MIT License" -> url("http://www.opensource.org/licenses/mit-license.php")),
    homepage := Some(url("https://github.com/killaitis/peloton")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/killaitis/peloton"),
        "https://github.com/killaitis/peloton.git"
      )
    ),
    developers := List(
      Developer(
        id    = "killaitis",
        name  = "Andreas Killaitis",
        email = "andreas@killaitis.de",
        url   = url("http://www.github.com/killaitis/")
      )
    )
  )
