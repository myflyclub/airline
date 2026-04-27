name := """airline-data"""

version := "5.1-SNAPSHOT"

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.19" % "test",
  "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0",
  "com.mysql" % "mysql-connector-j" % "9.7.0",
  "com.appoptics.agent.java" % "appoptics-sdk" % "6.13.0",
  "org.apache.pekko" %% "pekko-actor" % "1.0.3",
  "org.apache.pekko" %% "pekko-stream" % "1.0.3",
  "org.apache.pekko" %% "pekko-remote" % "1.0.3",
  "org.apache.pekko" %% "pekko-testkit" % "1.0.3",
  "org.apache.pekko" %% "pekko-cluster" % "1.0.3",
  "com.typesafe.play" %%  "play-json" % "2.10.8",
  "org.apache.commons" % "commons-math3" % "3.6.1",
  "com.zaxxer" % "HikariCP" % "5.1.0",
  "com.github.ben-manes.caffeine" % "caffeine" % "3.2.3")
