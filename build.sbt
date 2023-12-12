import Dependencies._

ThisBuild / version := {
  val Tag = "refs/tags/(.*)".r
  sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
    .getOrElse("0.0.1-SNAPSHOT")
}
ThisBuild / versionScheme := Some("semver-spec")

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
  .aggregate(core, postgresql)
  .settings(
    name                      := "peloton",
    description               := "Actors for Cats Effect",
    publish / skip            := true
  )

lazy val core = (project in file("core"))
  .settings(
    name                      := "peloton-core",
    description               := "The Peloton core library",

    publishTo                 := sonatypePublishToBundle.value,
    publishMavenStyle         := true,
    
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

      // Http4s
      "org.http4s" %% "http4s-dsl"                        % Http4sVersion,
      "org.http4s" %% "http4s-ember-server"               % Http4sVersion,
      "org.http4s" %% "http4s-ember-client"               % Http4sVersion,
      "org.http4s" %% "http4s-circe"                      % Http4sVersion,

      // Kryo Serialization
      "io.altoo" %% "scala-kryo-serialization"            % KryoSerializationVersion,

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

lazy val postgresql = (project in file("persistence/postgresql"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-persistence-postgresql",
    description               := "Peloton persistence PostgreSQL driver",
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      // Doobie
      "org.tpolecat" %% "doobie-core"                     % DoobieVersion,
      "org.tpolecat" %% "doobie-hikari"                   % DoobieVersion,
    )
  )

lazy val `integration-tests` = (project in file("integration-tests"))
  .dependsOn(core, postgresql)
  .settings(
    name                      := "peloton-integration-tests",
    description               := "Peloton integration tests",
    
    publish / skip            := true,

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

lazy val examples = (project in file("examples"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-examples",
    description               := "Peloton examples",
    
    publish / skip            := true,

    Test / parallelExecution  := false,
  )
