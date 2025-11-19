scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  // "-Xfatal-warnings",
  "-language:reflectiveCalls",
)

scalaVersion := "2.13.16"
val chiselVersion = "6.7.0"
addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "6.0.0"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.19.0" % "test"
libraryDependencies += "org.scalanlp" %% "breeze" % "2.1.0" // for FFT verification
libraryDependencies += "org.scalanlp" %% "breeze-natives" % "2.1.0" // for FFT verification
libraryDependencies += "com.fazecast" % "jSerialComm" % "2.10.0" // for UART
