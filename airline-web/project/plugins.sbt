// The Play plugin
addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.5")

// sbt-web and related plugins
addSbtPlugin("com.github.sbt" % "sbt-web" % "1.5.0")
addSbtPlugin("com.github.sbt" % "sbt-less" % "2.0.1")
addSbtPlugin("com.github.sbt" % "sbt-digest" % "2.1.0")

// Dependency scheme resolution
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always