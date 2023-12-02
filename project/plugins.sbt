// sbt assembly
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")

addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)

addDependencyTreePlugin
