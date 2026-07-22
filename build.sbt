lazy val Benchmark = 
  config("benchmark")
    .extend(Test)
    .describedAs("Benchmark tests")

lazy val commonPublishSettings = Seq(
  organization         := "de.killaitis",
  organizationName     := "Andreas Killaitis",
  organizationHomepage := Some(url("https://www.github.com/killaitis")),
  
  useGpg := false,
  
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

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "://sonatype.com",
  sys.env.getOrElse("SONATYPE_USERNAME", ""),
  sys.env.getOrElse("SONATYPE_PASSWORD", "")
)

version := {
  val Tag = "refs/tags/(.*)".r
  sys.env.get("CI_VERSION")
    .collect { case Tag(tag) => tag.stripPrefix("v") }
    .getOrElse("0.0.1-SNAPSHOT")
}

versionScheme := Some("semver-spec")

scalaVersion := "3.3.8"

javacOptions ++= Seq("--release", "11")

scalacOptions := Seq(
  "-source:future",
  "-unchecked",
  "-deprecation",
  "-Wunused:all",
  "-Wnonunit-statement",
  "-Wvalue-discard",
  "-release", "11"
)

lazy val root = (project in file("."))
  .aggregate(
    core, 
    `persistence-postgresql`, 
    `persistence-mysql`, 
    `persistence-cassandra`, 
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

    commonPublishSettings,

    Test / parallelExecution  := false,
    
    libraryDependencies ++= Seq(
      // Cats + Cats Effect
      "org.typelevel" %% "cats-effect"                        % Versions.CatsEffect,

      // Circe Json
      "io.circe" %% "circe-generic"                           % Versions.Circe,
      "io.circe" %% "circe-core"                              % Versions.Circe,
      "io.circe" %% "circe-parser"                            % Versions.Circe,

      // Http4s
      "org.http4s" %% "http4s-dsl"                            % Versions.Http4s,
      "org.http4s" %% "http4s-ember-server"                   % Versions.Http4s,
      "org.http4s" %% "http4s-ember-client"                   % Versions.Http4s,
      "org.http4s" %% "http4s-circe"                          % Versions.Http4s,

      // Kryo Serialization
      "io.altoo" %% "scala-kryo-serialization"                % Versions.KryoSerialization,

      // Config
      "com.github.pureconfig" %% "pureconfig-generic-scala3"  % Versions.PureConfig,
      "com.github.pureconfig" %% "pureconfig-cats-effect"     % Versions.PureConfig,

      // Logging
      "org.typelevel" %% "log4cats-slf4j"                     % Versions.Log4Cats,

      // Testing
      "org.scalatest" %% "scalatest"                          % Versions.ScalaTest          % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest"      % Versions.CatsEffectTesting  % Test,
      "ch.qos.logback" % "logback-classic"                    % Versions.Logback            % Test
    )
  )

lazy val `persistence-postgresql` = (project in file("persistence/postgresql"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-persistence-postgresql",
    description               := "Peloton persistence driver for PostgreSQL",
    
    commonPublishSettings,
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      // Doobie
      "org.typelevel" %% "doobie-core"   % Versions.Doobie,
      "org.typelevel" %% "doobie-hikari" % Versions.Doobie,

      // PostgreSQL JDBC driver
      "org.postgresql" % "postgresql"   % Versions.Postgres
    )
  )

lazy val `persistence-mysql` = (project in file("persistence/mysql"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-persistence-mysqll",
    description               := "Peloton persistence driver for MySQL / MariaDB",
    
    commonPublishSettings,
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      // Doobie
      "org.typelevel" %% "doobie-core"   % Versions.Doobie,
      "org.typelevel" %% "doobie-hikari" % Versions.Doobie,

      // MySQL JDBC driver
      "com.mysql" % "mysql-connector-j" % Versions.MySQL
    )
  )

lazy val `persistence-cassandra` = (project in file("persistence/cassandra"))
  .dependsOn(core)
  .settings(
    name                      := "peloton-persistence-cassandra",
    description               := "Peloton persistence driver for Apache Cassandra 4.x",
    
    commonPublishSettings,
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core"                  % Versions.Fs2,
      // "co.fs2" %% "fs2-reactive-streams"      % Fs2Version,
      "org.reactivestreams" % "reactive-streams-flow-adapters" % Versions.RSFlowAdapters,


      // Cassandra Java Driver
      "com.datastax.oss" % "java-driver-core" % Versions.CassandraJavaDriver
    )
  )

lazy val `scheduling-cron` = (project in file("scheduling/cron"))
  .dependsOn(core)
  .settings(    
    name                      := "peloton-scheduling-cron",
    description               := "Peloton CRON timer scheduling support for Cats Effect",
    
    commonPublishSettings,
    
    Test / parallelExecution  := false,

    libraryDependencies ++= Seq(
      // Quartz Scheduler
      ("org.quartz-scheduler" % "quartz"                   % Versions.QuartzScheduler)
        .exclude(
          "com.zaxxer", 
          "HikariCP-java7"
        ),

      // Testing
      "org.scalatest" %% "scalatest"                      % Versions.ScalaTest          % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest"  % Versions.CatsEffectTesting  % Test,
      "ch.qos.logback" % "logback-classic"                % Versions.Logback            % Test,
    )
  )

// Integration tests. Can be run with `sbt integration-tests/test`
lazy val `integration-tests` = (project in file("integration-tests"))
  .dependsOn(
    core, 
    `persistence-postgresql`,
    `persistence-mysql`,
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
      "org.scalatest" %% "scalatest"                      % Versions.ScalaTest          % Test,
      "org.typelevel" %% "cats-effect-testing-scalatest"  % Versions.CatsEffectTesting  % Test,
      "ch.qos.logback" % "logback-classic"                % Versions.Logback            % Test,
      "org.testcontainers" % "testcontainers"             % Versions.TestContainers     % Test,
      "org.testcontainers" % "postgresql"                 % Versions.TestContainers     % Test,
      "org.testcontainers" % "mysql"                      % Versions.TestContainers     % Test,
      "org.testcontainers" % "cassandra"                  % Versions.TestContainers     % Test
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
