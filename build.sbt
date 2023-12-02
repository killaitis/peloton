import Dependencies._

ThisBuild / organization         := "de.killaitis"
ThisBuild / organizationName     := "Andreas Killaitis"
ThisBuild / organizationHomepage := Some(url("https://www.github.com/killaitis/peloton"))

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.1"
ThisBuild / scalacOptions := Seq(
  "-source:future",
  "-unchecked",
  "-deprecation",
  "-Wunused:all",
  "-Wnonunit-statement",
  "-Wvalue-discard"
)

lazy val root = (project in file("."))
  .aggregate(core)
  .disablePlugins(AssemblyPlugin)
  .settings(
    description := "An actor library for Cats Effect",

    addCommandAlias("playground",   ";core/testOnly **.Playground")
  )

lazy val core = (project in file("core"))
  .settings(
    description := "The Peloton core library",
    
    assembly / assemblyJarName := "peloton-core.jar",

    Test / parallelExecution  := false,
    
    libraryDependencies ++= Seq(
      // Cats + Cats Effect
      "org.typelevel" %% "cats-effect"                    % CatsEffectVersion,

      // Monocle
      "dev.optics" %% "monocle-core"                      % MonocleVersion,
      "dev.optics" %% "monocle-macro"                     % MonocleVersion,

      // Circe Json
      "io.circe" %% "circe-generic"                       % CirceVersion,
      "io.circe" %% "circe-core"                          % CirceVersion,
      "io.circe" %% "circe-parser"                        % CirceVersion,

      // Doobie
      "org.tpolecat" %% "doobie-core"                     % DoobieVersion,
      "org.tpolecat" %% "doobie-hikari"                   % DoobieVersion,
      "org.http4s"   %% "http4s-dsl"                      % Http4sVersion, // Needed to evict fs2 version provided by doobie

      // Config
      "com.github.pureconfig" %% "pureconfig-core"        % PureConfigVersion,

      // Logging
      "org.typelevel" %% "log4cats-slf4j"                 % Log4CatsVersion,

      // Testing
      "org.scalatest" %% "scalatest"                      % ScalaTestVersion          % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest"  % CatsEffectTestingVersion  % Test,
      "ch.qos.logback" % "logback-classic"                % LogbackVersion            % Test
    )
  )

lazy val `integration-tests` = (project in file("integration-tests"))
  .dependsOn(core)
  .disablePlugins(AssemblyPlugin)
  .settings(
    description               := "Integration tests",
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      // Testing
      "org.scalatest" %% "scalatest"                      % ScalaTestVersion          % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest"  % CatsEffectTestingVersion  % Test,
      "ch.qos.logback" % "logback-classic"                % LogbackVersion            % Test,
      "org.postgresql" % "postgresql"                     % PostgresVersion           % Test,
      "org.testcontainers" % "testcontainers"             % TestContainersVersion     % Test,
      "org.testcontainers" % "postgresql"                 % TestContainersVersion     % Test
    )
  )
