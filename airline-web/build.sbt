name := "airline-web"

version := "5.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.13.18"

// Force SBT to run the Play app in a separate JVM
fork := true

// Configure the forked JVM for a stateless web profile
javaOptions ++= Seq(
  "-Xms2G", 
  "-Xmx2G", 
  "-XX:+UseG1GC"
)

libraryDependencies ++= Seq(
  jdbc,
  ws,
  guice,
  specs2 % Test,
  "org.apache.pekko" %% "pekko-testkit" % "1.0.3" % Test,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.apache.pekko" %% "pekko-remote" % "1.0.3",
  "org.apache.commons" % "commons-text" % "1.15.0",
  "default" %% "airline-data" % "5.1-SNAPSHOT",

  "com.google.api-client" % "google-api-client" % "1.35.0",
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.39.0",
  "com.google.apis" % "google-api-services-gmail" % "v1-rev20260413-2.0.0",
  "com.google.photos.library" % "google-photos-library-client" % "1.7.3",

  // Legacy Mail API
  "javax.mail" % "javax.mail-api" % "1.6.2",
  "com.sun.mail" % "javax.mail" % "1.6.2"
)

routesGenerator := InjectedRoutesGenerator

Assets / pipelineStages := Seq(digest)
