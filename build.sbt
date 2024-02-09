import Dependencies._

lazy val Benchmark = config("benchmark") extend Test describedAs "Benchmark tests"

ThisBuild / version := {
  val Tag = "refs/tags/(.*)".r
  sys.env.get("CI_VERSION")
    .collect { case Tag(tag) => tag.stripPrefix("v") }
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
  .aggregate(
    core, 
    `persistence-postgresql`, 
    `scheduling-cron`
  )
  .settings(
    name                      := "peloton",
    description               := "Actors for Cats Effect",
    publish / skip            := true,

    Test / parallelExecution  := false
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

lazy val `persistence-postgresql` = (project in file("persistence/postgresql"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-persistence-postgresql",
    description               := "Peloton persistence driver for PostgreSQL",
    
    publishTo                 := sonatypePublishToBundle.value,
    publishMavenStyle         := true,
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      // Doobie
      "org.tpolecat" %% "doobie-core"                     % DoobieVersion,
      "org.tpolecat" %% "doobie-hikari"                   % DoobieVersion,
    )
  )

lazy val `persistence-cassandra` = (project in file("persistence/cassandra"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-persistence-cassandra",
    description               := "Peloton persistence driver for Apache Cassandra 4.x",
    
    publishTo                 := sonatypePublishToBundle.value,
    publishMavenStyle         := true,
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core"                  % Fs2Version,
      // "co.fs2" %% "fs2-reactive-streams"      % Fs2Version,
      "org.reactivestreams" % "reactive-streams-flow-adapters" % "1.0.2",


      // Cassandra Java Driver
      "com.datastax.oss" % "java-driver-core" % CassandraJavaDriverVersion
    )
  )

lazy val `scheduling-cron` = (project in file("scheduling/cron"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-scheduling-cron",
    description               := "Peloton CRON timer scheduling support for Cats Effect",
    
    publishTo                 := sonatypePublishToBundle.value,
    publishMavenStyle         := true,
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      // Quartz Scheduler
      "org.quartz-scheduler" % "quartz"                   % QuartzSchedulerVersion 
        exclude (
          "com.zaxxer", 
          "HikariCP-java7"
        ),

      // Testing
      "org.scalatest" %% "scalatest"                      % ScalaTestVersion          % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest"  % CatsEffectTestingVersion  % Test,
      "ch.qos.logback" % "logback-classic"                % LogbackVersion            % Runtime,
    )
  )

lazy val `integration-tests` = (project in file("integration-tests"))
  .dependsOn(
    core, 
    `persistence-postgresql`, 
    `persistence-cassandra`, 
    `scheduling-cron`
  )
  .configs(Benchmark)
  .settings(
    name                      := "peloton-integration-tests",
    description               := "Peloton integration tests",
    
    publish / skip            := true,

    Test / parallelExecution  := false,

    // Skip all benchmarks from regular integration tests
    Test / testOptions        := Seq(Tests.Argument("-l", "Benchmark")),

    // Create a separate benchmark suite (sbt integration-tests/testOnly -- -n Benchmark)
    inConfig(Benchmark)(Defaults.testTasks),
    Benchmark / parallelExecution := false,
    Benchmark / testOptions       := Seq(Tests.Argument("-n", "Benchmark")),


    libraryDependencies ++= Seq(
      // Testing
      "org.scalatest" %% "scalatest"                      % ScalaTestVersion          % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest"  % CatsEffectTestingVersion  % Test,
      "ch.qos.logback" % "logback-classic"                % LogbackVersion            % Test,
      "org.postgresql" % "postgresql"                     % PostgresVersion           % Test,
      "org.testcontainers" % "testcontainers"             % TestContainersVersion     % Test,
      "org.testcontainers" % "postgresql"                 % TestContainersVersion     % Test,
      "org.testcontainers" % "cassandra"                  % TestContainersVersion     % Test
    )
  )

lazy val examples = (project in file("examples"))
  .dependsOn(
    core, 
    `persistence-postgresql`, 
    `scheduling-cron`
  )
  .settings(
    name                      := "peloton-examples",
    description               := "Peloton examples",
    
    publish / skip            := true,

    Test / parallelExecution  := false,
  )
