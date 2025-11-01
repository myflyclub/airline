name := """airline-web"""

version := "4.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.13.11"

libraryDependencies ++= Seq(
  jdbc,
  ws,
  guice,
  "com.google.inject" % "guice" % "5.1.0",
  "com.google.inject.extensions" % "guice-assistedinject" % "5.1.0",
  specs2 % Test,
  "org.apache.pekko" %% "pekko-remote" % "1.0.3",
  "org.apache.commons" % "commons-text" % "1.13.0",
  "default" %% "airline-data" % "4.1-SNAPSHOT",
  "com.google.api-client" % "google-api-client" % "1.30.4",
  "com.google.oauth-client" % "google-oauth-client-jetty" % "1.34.1",
  "com.google.apis" % "google-api-services-gmail" % "v1-rev103-1.25.0",
  "com.google.photos.library" % "google-photos-library-client" % "1.7.2",
  "javax.mail" % "javax.mail-api" % "1.6.2",
  "com.sun.mail" % "javax.mail" % "1.6.2"
)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

routesGenerator := InjectedRoutesGenerator

// Enable asset pipeline with digest
Assets / pipelineStages := Seq(digest)

// Optional: Include all files in digest (not just .js, .css)
includeFilter in digest := "*.js" || "*.css" || "*.png" || "*.jpg" || "*.gif"