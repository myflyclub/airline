name := "airline-web"

version := "4.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.13.11"

libraryDependencies ++= Seq(
  jdbc,
  ws,
  guice,
  specs2 % Test,
  "org.apache.pekko" %% "pekko-remote" % "1.0.3",
  "org.apache.commons" % "commons-text" % "1.13.0",
  "default" %% "airline-data" % "4.1-SNAPSHOT",
  
  "com.google.api-client" % "google-api-client" % "1.35.0",
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.34.1",
  "com.google.apis" % "google-api-services-gmail" % "v1-rev20231218-2.0.0",
  "com.google.photos.library" % "google-photos-library-client" % "1.7.3",
  
  // Legacy Mail API
  "javax.mail" % "javax.mail-api" % "1.6.2",
  "com.sun.mail" % "javax.mail" % "1.6.2"
)

routesGenerator := InjectedRoutesGenerator

Assets / pipelineStages := Seq(digest)

Assets / digest / includeFilter := "*.js" || "*.css" || "*.png" || "*.jpg" || "*.gif"