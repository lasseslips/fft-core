package buildtools

import java.nio.file.{Files, Paths, Path, StandardOpenOption}
import java.io.PrintWriter
import scala.util.matching.Regex
import scala.sys.process._
import ujson._
import java.io.File
import scala.jdk.CollectionConverters._
import java.nio.charset.StandardCharsets

object CollectParametricMetrics {

  case class Metrics(
    run: Int,
    fftSize: String,
    width: String,
    binaryPoint: String,
    pipeline: String,
    architecture: String,
    wns: Option[Double],
    maxFreq: Option[Double],
    fftWns: Option[Double],
    fftMaxFreq: Option[Double],
    luts: Option[Int],
    lutPct: Option[Double],
    ffs: Option[Int],
    ffPct: Option[Double],
    dsps: Option[Int],
    dspPct: Option[Double],
    fftLuts: Option[Int],
    fftLutPct: Option[Double],
    fftFfs: Option[Int],
    fftFfPct: Option[Double],
    fftDsps: Option[Int],
    fftDspPct: Option[Double],
    compileTime: Option[Double],
    synthTime: Option[Double],
    totalTime: Option[Double],
    success: Boolean
  )

  def parseTimingReport(path: Path): (Option[Double], Option[Double]) = {
    if (!Files.exists(path)) return (None, None)
    val content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    val slackRe = new Regex("Setup\\s*:.*?Worst Slack\\s*([\\d\\.\\-]+)ns", "s")
    slackRe.findFirstMatchIn(content) match {
      case Some(m) =>
        val slack = m.group(1).toDouble
        val targetPeriodNs = 10.0
        val actualPeriodNs = targetPeriodNs - slack
        val maxFreq = if (actualPeriodNs > 0) 1000.0 / actualPeriodNs else 1000.0 / targetPeriodNs
        (Some(slack), Some(maxFreq))
      case None => (None, None)
    }
  }

  def parseFFTCoreTiming(path: Path): (Option[Double], Option[Double]) = {
    if (!Files.exists(path)) return (None, None)
    val content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    val pathSections = content.split("Slack ")
    val targetPeriod = 10.0
    val fftSlacks = pathSections.flatMap { sec =>
      if (sec.contains("fftCore")) {
        ":\\s*([\\d\\.-]+)ns".r.findFirstMatchIn(sec).map(_.group(1).toDouble)
      } else None
    }
    if (fftSlacks.nonEmpty) {
      val worst = fftSlacks.min
      val actual = targetPeriod - worst
      val freq = if (actual > 0) 1000.0 / actual else 1000.0 / targetPeriod
      (Some(worst), Some(freq))
    } else (None, None)
  }

  def parseUtilizationReport(path: Path): (Option[Int], Option[Double], Option[Int], Option[Double], Option[Int], Option[Double]) = {
    if (!Files.exists(path)) return (None, None, None, None, None, None)
    val content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    val lutRe = "\\|\\s*Slice LUTs[^|]*\\|\\s*(\\d+)\\s*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\s*(\\d+\\.?\\d*)\\s*\\|".r
    val ffRe = "\\|\\s*Slice Registers[^|]*\\|\\s*(\\d+)\\s*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\s*(\\d+\\.?\\d*)\\s*\\|".r
    val dspRe = "\\|\\s*DSPs[^|]*\\|\\s*(\\d+)\\s*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\s*(\\d+\\.?\\d*)\\s*\\|".r
    val lut = lutRe.findFirstMatchIn(content).map(m => (m.group(1).toInt, m.group(2).toDouble))
    val ff = ffRe.findFirstMatchIn(content).map(m => (m.group(1).toInt, m.group(2).toDouble))
    val dsp = dspRe.findFirstMatchIn(content).map(m => (m.group(1).toInt, m.group(2).toDouble))
    (lut.map(_._1), lut.map(_._2), ff.map(_._1), ff.map(_._2), dsp.map(_._1), dsp.map(_._2))
  }

  def parseHierUtil(path: Path, search: String): (Option[Int], Option[Double], Option[Int], Option[Double], Option[Int], Option[Double]) = {
    if (!Files.exists(path)) return (None, None, None, None, None, None)
    val content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    val regex = ("\\|\\s*" + java.util.regex.Pattern.quote(search) + "\\s*\\|[^|]*\\|\\s*(\\d+)\\s*\\|[^|]*\\|[^|]*\\|[^|]*\\|\\s*(\\d+)\\s*\\|[^|]*\\|[^|]*\\|\\s*(\\d+)\\s*\\|").r
    regex.findFirstMatchIn(content) match {
      case Some(m) =>
        val luts = m.group(1).toInt
        val ffs = m.group(2).toInt
        val dsps = m.group(3).toInt
        val lutPct = luts.toDouble / 20800.0 * 100.0
        val ffPct = ffs.toDouble / 41600.0 * 100.0
        val dspPct = if (dsps > 0) dsps.toDouble / 90.0 * 100.0 else 0.0
        (Some(luts), Some(lutPct), Some(ffs), Some(ffPct), Some(dsps), Some(dspPct))
      case None => (None, None, None, None, None, None)
    }
  }

  def cleanupOldResults(): Unit = {
    val names = Seq("reports", "reports/logs", "reports/logs/vivado.log", "reports/logs/vivado.jou", ".Xil")
    names.foreach { n =>
      val p = Paths.get(n)
      try {
        if (Files.exists(p)) {
          if (Files.isDirectory(p)) {
            import scala.reflect.io.Directory
            val dir = new Directory(new File(p.toString))
            dir.deleteRecursively()
          } else Files.delete(p)
        }
      } catch {
        case e: Exception => System.err.println(s"Warning: Could not remove $p: ${e.getMessage}")
      }
    }
  }

  def generateParameterSets(config: Map[String, Any]): Seq[Map[String, Any]] = {
    val base = config.getOrElse("base_parameters", Map("fftSize" -> 8, "width" -> 16, "binaryPoint" -> 8, "pipeline" -> true)).asInstanceOf[Map[String, Any]]
    config.get("parameter_sweep") match {
      case Some(s: Map[String, Any] @unchecked) =>
        s.get("type") match {
          case Some("fft_size") => s.get("values").map { v => v.asInstanceOf[List[Any]].map(x => base + ("fftSize" -> x)) }.getOrElse(Seq(base))
          case Some("data_width") => s.get("values").map { v => v.asInstanceOf[List[Any]].map(x => base + ("width" -> x)) }.getOrElse(Seq(base))
          case Some("architecture") => s.get("values").map { v => v.asInstanceOf[List[Any]].map(x => base + ("architecture" -> x)) }.getOrElse(Seq(base))
          case Some("pipeline") => s.get("values").map { v => v.asInstanceOf[List[Any]].map(x => base + ("pipeline" -> x)) }.getOrElse(Seq(base))
          case Some("multiple") =>
            val params = s.getOrElse("parameters", Map.empty).asInstanceOf[Map[String, List[Any]]]
            val keys = params.keys.toList
            val values = keys.map(k => params(k))
            val combos = values.foldLeft(Seq(Seq.empty[Any])) { (acc, next) => for (a <- acc; n <- next) yield a :+ n }
            combos.map { combo =>
              keys.zip(combo).foldLeft(base) { case (m, (k, v)) => m + (k -> v) }
            }
          case _ => Seq(base)
        }
      case _ => Seq(base)
    }
  }

  def createScalaMain(params: Map[String, Any], outputPath: Path): Path = {
    val fftSize = params.getOrElse("fftSize", 8)
    val width = params.getOrElse("width", 16)
    val binaryPoint = params.getOrElse("binaryPoint", 8)
    val pipeline = params.getOrElse("pipeline", true)
    val architecture = params.getOrElse("architecture", "GS").toString
    val content = s"""
import chisel3._
import verifier.FFTTestData
/**
 * Auto-generated Main.scala with parameters: $params
 */
object Main extends App {
  println("I will now generate the Verilog file!")
  val fftSize = $fftSize
  val width = $width
  val binaryPoint = $binaryPoint
  val pipeline = ${pipeline.toString.toLowerCase}
  val architecture = "$architecture"
  val testCases = Seq(
      FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
      FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
  )
  emitVerilog(new FPGATestTop(fftSize, width, binaryPoint, pipeline, testCases), Array("--target-dir", "verilog"))
}
"""
    Files.write(outputPath, content.getBytes(StandardCharsets.UTF_8))
    outputPath
  }

  def compileChiselDesign(params: Map[String, Any], runNumber: Int): (Boolean, Option[Double]) = {
    println(s"  Compiling Chisel design with parameters: $params")
    val projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath
    val originalMain = projectRoot.resolve("src/main/scala/Main.scala")
    val backupMain = projectRoot.resolve("src/main/scala/Main.scala.backup")
    try {
      if (Files.exists(originalMain)) Files.copy(originalMain, backupMain, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      val tmpMain = projectRoot.resolve("src/main/scala/Main.scala.tmp")
      createScalaMain(params, tmpMain)
      Files.move(tmpMain, originalMain, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        // Build a bash -c command that uses wslpath to convert the Windows path to WSL path.
        val windowsPathEscaped = projectRoot.toAbsolutePath.toString.replace("\\", "\\\\")
        val bashCmd = "cd $(wslpath -u '" + windowsPathEscaped + "') && sbt 'runMain Main'"
        val wslCmd = Seq("wsl", "bash", "-c", bashCmd)
      val start = System.nanoTime()
      val pb = new java.lang.ProcessBuilder(wslCmd.asJava)
      pb.redirectErrorStream(true)
      val process = pb.start()
      val out = scala.io.Source.fromInputStream(process.getInputStream).getLines().mkString("\n")
      val rc = process.waitFor()
      val elapsed = (System.nanoTime() - start) / 1e9
      if (rc != 0) {
        println("  Chisel compilation failed:")
        println(out)
        (false, Some(elapsed))
      } else {
        println(f"  Chisel compilation completed in ${elapsed}%.1fs")
        (true, Some(elapsed))
      }
    } catch {
      case e: Exception => println(s"Error during Chisel compile: ${e.getMessage}"); (false, None)
    } finally {
      if (Files.exists(backupMain)) {
        Files.move(backupMain, originalMain, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }

  def runParametricBuild(version: Option[String], tclFile: Path, params: Map[String, Any], runNumber: Int): Option[Metrics] = {
    println(s"Starting parametric run $runNumber...")
    println(s"  Parameters: $params")
    cleanupOldResults()
    val (chiselOk, compileTimeOpt) = compileChiselDesign(params, runNumber)
    if (!chiselOk) { println(s"  Run $runNumber failed during Chisel compilation"); return None }
    println("  Running Vivado synthesis (Scala wrapper)...")
    val start = System.nanoTime()
    val rc = try {
      val args = (version.toSeq.flatMap(v => Seq("--version", v)) ++ Seq("--tcl", tclFile.toString)).toArray
      RunVivadoScript.run(args)
    } catch {
      case e: Exception => println(s"  Exception while running Vivado: ${e.getMessage}"); -1
    }
    val synthTime = (System.nanoTime() - start) / 1e9
    if (rc != 0) {
      println(s"  Run $runNumber failed during Vivado synthesis (rc=$rc)")
      return None
    }
    val timingFile = Paths.get("reports/timing_summary.txt")
    val timingDetailed = Paths.get("reports/timing_detailed.txt")
    val utilFile = Paths.get("reports/utilization_report.txt")
    val hierFile = Paths.get("reports/utilization_hierarchical.txt")
    val (wns, maxFreq) = parseTimingReport(timingFile)
    val (fftWns, fftMaxFreq) = parseFFTCoreTiming(timingDetailed)
    val (luts, lutPct, ffs, ffPct, dsps, dspPct) = parseUtilizationReport(utilFile)
    val (fftLuts, fftLutPct, fftFfs, fftFfPct, fftDsps, fftDspPct) = parseHierUtil(hierFile, "fftCore")
    val (ifftLuts, ifftLutPct, ifftFfs, ifftFfPct, ifftDsps, ifftDspPct) = parseHierUtil(hierFile, "ifftCore")
    val totalTime = compileTimeOpt.getOrElse(0.0) + synthTime
    val metrics = Metrics(
      runNumber,
      params.getOrElse("fftSize", "N/A").toString,
      params.getOrElse("width", "N/A").toString,
      params.getOrElse("binaryPoint", "N/A").toString,
      params.getOrElse("pipeline", "N/A").toString,
      params.getOrElse("architecture", "N/A").toString,
      wns, maxFreq, fftWns, fftMaxFreq,
      luts, lutPct, ffs, ffPct, dsps, dspPct,
      fftLuts, fftLutPct, fftFfs, fftFfPct, fftDsps, fftDspPct,
      compileTimeOpt, Some(synthTime), Some(totalTime), true
    )
    println(s"  Run $runNumber completed - WNS: ${wns.getOrElse(Double.NaN)}ns, Max Freq: ${maxFreq.getOrElse(Double.NaN)}MHz")
    Some(metrics)
  }

  def loadDefaultConfig(): Map[String, Any] = Map(
    "base_parameters" -> Map("fftSize" -> 8, "width" -> 16, "binaryPoint" -> 8, "pipeline" -> true, "architecture" -> "GS"),
    "parameter_sweep" -> Map("type" -> "fft_size", "values" -> List(4, 8, 16))
  )

  def writeCsv(path: Path, results: Seq[Metrics]): Unit = {
    val header = Seq(
      "run", "fft_size", "data_width", "binary_point", "pipeline",
      "architecture",
      "wns_ns", "max_frequency_mhz", "fft_wns_ns", "fft_max_frequency_mhz",
      "luts_used", "lut_percentage", "ffs_used", "ffs_percentage",
      "dsps_used", "dsp_percentage",
      "fft_luts_used", "fft_lut_percentage", "fft_ffs_used", "fft_ffs_percentage",
      "fft_dsps_used", "fft_dsp_percentage",
      "ifft_luts_used", "ifft_lut_percentage", "ifft_ffs_used", "ifft_ffs_percentage",
      "ifft_dsps_used", "ifft_dsp_percentage",
      "compile_time_s", "synthesis_time_s", "total_time_s", "success"
    )
    val pw = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))
    try {
      pw.println(header.mkString(","))
      results.foreach { r =>
        val row = Seq(
          r.run.toString, r.fftSize, r.width, r.binaryPoint, r.pipeline,
          r.architecture,
          r.wns.map(_.toString).getOrElse(""), r.maxFreq.map(_.toString).getOrElse(""), r.fftWns.map(_.toString).getOrElse(""), r.fftMaxFreq.map(_.toString).getOrElse(""),
          r.luts.map(_.toString).getOrElse(""), r.lutPct.map(_.toString).getOrElse(""), r.ffs.map(_.toString).getOrElse(""), r.ffPct.map(_.toString).getOrElse(""),
          r.dsps.map(_.toString).getOrElse(""), r.dspPct.map(_.toString).getOrElse(""),
          r.fftLuts.map(_.toString).getOrElse(""), r.fftLutPct.map(_.toString).getOrElse(""), r.fftFfs.map(_.toString).getOrElse(""), r.fftFfPct.map(_.toString).getOrElse(""),
          r.fftDsps.map(_.toString).getOrElse(""), r.fftDspPct.map(_.toString).getOrElse(""),
          r.compileTime.map(_.toString).getOrElse(""), r.synthTime.map(_.toString).getOrElse(""), r.totalTime.map(_.toString).getOrElse(""), r.success.toString
        )
        pw.println(row.mkString(","))
      }
    } finally pw.close()
  }

  def main(args: Array[String]): Unit = {
    var configFile: Option[String] = None
    var version: Option[String] = None
    var tcl: Option[String] = None
    var output = "parametric_results.csv"
    var runs: Option[Int] = None
    var i = 0
    while (i < args.length) {
      args(i) match {
        case "--config" | "-c" => configFile = Some(args(i + 1)); i += 2
        case "--version" | "-v" => version = Some(args(i + 1)); i += 2
        case "--tcl" => tcl = Some(args(i + 1)); i += 2
        case "--output" | "-o" => output = args(i + 1); i += 2
        case "--vivado-script" => println("Warning: --vivado-script ignored; using Scala RunVivadoScript wrapper"); i += 2
        case "--runs" | "-r" => runs = Some(args(i + 1).toInt); i += 2
        case other => println(s"Unknown arg: $other"); i += 1
      }
    }

    def ujsonToAny(v: ujson.Value): Any = v match {
      case ujson.Num(n) if n.isValidInt => n.toInt
      case ujson.Num(n) => n.toDouble
      case ujson.Str(s) => s
      case ujson.Bool(b) => b
      case ujson.Arr(arr) => arr.toList.map(ujsonToAny)
      case ujson.Obj(obj) => obj.map { case (k, vv) => k -> ujsonToAny(vv) }.toMap
      case ujson.Null => null
    }

    val config: Map[String, Any] = configFile.flatMap { cf =>
      val p = Paths.get(cf)
      if (Files.exists(p)) {
        val raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
        try {
          val parsed = ujson.read(raw)
          Some(ujsonToAny(parsed).asInstanceOf[Map[String, Any]])
        } catch {
          case e: Exception =>
          System.err.println(s"Warning: Failed to parse config file $p: ${e.getMessage}. Using default config.")
          None
        }
      } else {
        System.err.println(s"Warning: Config file not found: $p. Using default config.")
        None
      }
    }.getOrElse(loadDefaultConfig())

    println(s"Using configuration: $config")

    val parameterSets = runs.map(r => Seq.fill(r)(config("base_parameters").asInstanceOf[Map[String, Any]])).getOrElse(generateParameterSets(config))

    val tclPath = tcl.map(Paths.get(_)).getOrElse(Paths.get("src/main/tcl/build.tcl"))
    if (!Files.exists(tclPath)) { System.err.println(s"Error: TCL file not found: $tclPath"); sys.exit(1) }

    println(s"Starting ${parameterSets.length} parametric runs...")
    println(s"Vivado script: (using Scala RunVivadoScript wrapper)")
    println(s"TCL file: $tclPath")
    println(s"Output file: $output")

    val results = scala.collection.mutable.ArrayBuffer[Metrics]()
    var successful = 0
    for ((paramsAny, idx) <- parameterSets.zipWithIndex) {
      val params = paramsAny.asInstanceOf[Map[String, Any]]
      val runNum = idx + 1
      val res = runParametricBuild(version, tclPath, params, runNum)
      res match {
        case Some(m) => results += m; successful += 1
        case None =>
          results += Metrics(
            run = runNum,
            fftSize = params.getOrElse("fftSize", "N/A").toString,
            width = params.getOrElse("width", "N/A").toString,
            binaryPoint = params.getOrElse("binaryPoint", "N/A").toString,
            pipeline = params.getOrElse("pipeline", "N/A").toString,
            architecture = params.getOrElse("architecture", "N/A").toString,
            wns = None,
            maxFreq = None,
            fftWns = None,
            fftMaxFreq = None,
            luts = None,
            lutPct = None,
            ffs = None,
            ffPct = None,
            dsps = None,
            dspPct = None,
            fftLuts = None,
            fftLutPct = None,
            fftFfs = None,
            fftFfPct = None,
            fftDsps = None,
            fftDspPct = None,
            compileTime = None,
            synthTime = None,
            totalTime = None,
            success = false
          )
      }
      println("-" * 40)
    }
    writeCsv(Paths.get(output), results.toSeq)
    println(s"\nResults saved to $output")
    println(s"Successful runs: $successful/${parameterSets.length}")
  }
}
