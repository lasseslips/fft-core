package buildtools

import java.nio.file.{Files, Paths, Path, StandardOpenOption}
import java.io.File
import scala.sys.process._

object RunVivadoScript {
  def run(args: Array[String]): Int = {
    case class Config(version: Option[String] = None, tcl: Option[String] = None, dryRun: Boolean = false)

    // Arg parsing
    var cfg = Config()
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--version" | "-v" => cfg = cfg.copy(version = Some(args(i + 1))); i += 2
        case "--tcl" => cfg = cfg.copy(tcl = Some(args(i + 1))); i += 2
        case "--dry-run" => cfg = cfg.copy(dryRun = true); i += 1
        case other => println(s"Unknown arg: $other"); i += 1
      }
    }

    // Assume working directory is project root when run via sbt
    val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath
    val defaultTcl = projectRoot.resolve("src/main/tcl/build.tcl")
    val tclPath = cfg.tcl.map(Paths.get(_)).getOrElse(defaultTcl)
    if (!Files.exists(tclPath)) {
      System.err.println(s"Error: build.tcl not found at $tclPath")
      return 2
    }

    def isWindows: Boolean = System.getProperty("os.name").toLowerCase.contains("windows")

    def findVivadoRoot(): Path = {
      val env = Option(System.getenv("XILINX"))
      val candidates = scala.collection.mutable.ArrayBuffer[Path]()
      env.foreach(e => candidates += Paths.get(e))
      if (isWindows) {
        candidates += Paths.get("C:/Xilinx/Vivado")
        candidates += Paths.get("C:/Xilinx")
        candidates.find(p => Files.exists(p)).getOrElse(Paths.get("C:/Xilinx/Vivado"))
      } else {
        candidates += Paths.get("/tools/Xilinx/Vivado")
        candidates += Paths.get("/opt/Xilinx/Vivado")
        candidates += Paths.get(System.getProperty("user.home") + "/Xilinx/Vivado")
        candidates.find(p => Files.exists(p)).getOrElse(Paths.get("/tools/Xilinx/Vivado"))
      }
    }

    def chooseVersion(root: Path, requested: Option[String]): Path = {
      requested match {
        case Some(ver) =>
          val candidate = root.resolve(ver)
          if (Files.exists(candidate) && Files.isDirectory(candidate)) candidate
          else throw new RuntimeException(s"Requested Vivado version not found: $candidate")
        case None =>
          if (!Files.exists(root) || !Files.isDirectory(root)) throw new RuntimeException(s"Vivado root not found: $root")
          import scala.jdk.CollectionConverters._
          val versions = Files.list(root).iterator().asScala.filter(Files.isDirectory(_)).toList
          if (versions.isEmpty) throw new RuntimeException(s"No Vivado versions found under $root")
          versions.sortBy(_.getFileName.toString)(Ordering[String].reverse).head
      }
    }

    def getSettingsScript(vivadoDir: Path): Path = {
      if (isWindows) vivadoDir.resolve("settings64.bat") else vivadoDir.resolve("settings64.sh")
    }

    val root = findVivadoRoot()
    val vivadoDir = try { chooseVersion(root, cfg.version) } catch { case e: Throwable => System.err.println(s"Error: ${e.getMessage}"); return 3 }
    val settings = getSettingsScript(vivadoDir)
    if (!Files.exists(settings)) {
      val scriptType = if (isWindows) "settings64.bat" else "settings64.sh"
      System.err.println(s"Error: $scriptType not found at $settings")
      return 4
    }

    val logsDir = projectRoot.resolve("reports/logs")
    if (!Files.exists(logsDir)) Files.createDirectories(logsDir)

    // Create temp script file
    val tmp = if (isWindows) Files.createTempFile("vivado_run_", ".bat") else Files.createTempFile("vivado_run_", ".sh")
    try {
      val content = if (isWindows) {
        s"@echo off\r\ncall \"${settings.toAbsolutePath}\"\r\nvivado -mode batch -source \"${tclPath.toAbsolutePath}\" -log \"${logsDir.resolve("vivado.log")}\" -journal \"${logsDir.resolve("vivado.jou")}\" -tempDir \"${logsDir.toAbsolutePath}\"\r\n"
      } else {
        s"#!/bin/bash\nsource \"${settings.toAbsolutePath}\"\nvivado -mode batch -source \"${tclPath.toAbsolutePath}\" -log \"${logsDir.resolve("vivado.log")}\" -journal \"${logsDir.resolve("vivado.jou")}\" -tempDir \"${logsDir.toAbsolutePath}\"\n"
      }
      Files.write(tmp, content.getBytes("UTF-8"), StandardOpenOption.TRUNCATE_EXISTING)
      if (!isWindows) tmp.toFile.setExecutable(true)

      if (cfg.dryRun) {
        println("Dry run command:")
        println(s"Platform: ${if (isWindows) "Windows" else "Unix-like"}")
        println(s"Logs directory: $logsDir")
        val scriptType = if (isWindows) "batch file" else "shell script"
        println(s"Temporary $scriptType would contain:")
        println(new String(Files.readAllBytes(tmp)))
        tmp.toFile.delete()
        return 0
      }

      val rc = if (isWindows) {
        Seq("cmd", "/c", tmp.toAbsolutePath.toString).!
      } else {
        Seq(tmp.toAbsolutePath.toString).!
      }
      if (rc != 0) System.err.println(s"Vivado exited with code $rc")
      rc
    } finally {
      if (Files.exists(tmp)) Files.delete(tmp)
    }
  }

  def main(args: Array[String]): Unit = sys.exit(run(args))
}
