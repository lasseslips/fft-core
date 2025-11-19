package uart

import com.fazecast.jSerialComm.SerialPort
import scala.concurrent.{Future, blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn
import utils.FixedPointUtils
import utils.ChunkUtils
import verifier.ScalaFFTVerifier


object UartTool extends App {
  def listPorts(): Array[SerialPort] = SerialPort.getCommPorts

  def choosePort(): Option[SerialPort] = {
    val ports = listPorts()
    if (ports.isEmpty) {
      println("No serial ports found.")
      None
    } else {
      println("Available serial ports:")
      ports.zipWithIndex.foreach { case (p, i) => println(s"  [$i] ${p.getSystemPortName} - ${p.getDescriptivePortName}") }
      print("Choose port index (or press Enter for 0): ")
      val line = StdIn.readLine()
      val idx = if (line.trim.isEmpty) 0 else try { line.toInt } catch { case _: Throwable => 0 }
      if (idx >= 0 && idx < ports.length) Some(ports(idx)) else None
    }
  }

  def parseBaud(arg: Option[String]): Int = arg.flatMap { s => try Some(s.toInt) catch { case _: Throwable => None } }.getOrElse(115200)

  // Entry
  val argsList = args.toList
  val maybePortName = argsList.lift(0)
  val baud = parseBaud(argsList.lift(1))

  val portOpt: Option[SerialPort] = maybePortName match {
    case Some(name) =>
      val p = SerialPort.getCommPort(name)
      Some(p)
    case None => choosePort()
  }

  portOpt match {
    case None => println("No port selected. Exiting.")
    case Some(port) =>
      port.setComPortParameters(baud, 8, SerialPort.TWO_STOP_BITS, SerialPort.NO_PARITY)
      port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
      if (!port.openPort()) {
        System.err.println(s"Failed to open port ${port.getSystemPortName}")
      } else {
        println(s"Opened ${port.getSystemPortName} (${port.getDescriptivePortName}) at ${baud} baud")

        @volatile var running = true

        // Reader thread
        Future {
          val in = port.getInputStream
          val buf = new Array[Byte](1024)
          try {
            while (running && port.isOpen) {
              blocking {
                val n = try in.read(buf) catch { case _: Throwable => -1 }
                if (n > 0) {
                  val r = buf.take(n)
                  val hex = r.map(b => f"$b%02x").mkString(" ")
                  println(s"\n< RX (${n} bytes): hex=$hex")
                  print("> ")
                } else {
                  Thread.sleep(10)
                }
              }
            }
          } finally {
            try in.close() catch { case _: Throwable => () }
          }
        }

        

        // Read up to N bytes or until timeout (ms)
        def readNBytes(n: Int, timeoutMs: Long = 5000): Array[Byte] = {
          val in = port.getInputStream
          val outBuf = scala.collection.mutable.ArrayBuffer.empty[Byte]
          val start = System.currentTimeMillis()
          val tmp = new Array[Byte](1024)
          while (outBuf.size < n && port.isOpen && (System.currentTimeMillis() - start) < timeoutMs) {
            val r = try in.read(tmp) catch { case _: Throwable => -1 }
            if (r > 0) {
              outBuf ++= tmp.take(r)
            } else {
              Thread.sleep(5)
            }
          }
          outBuf.toArray
        }

        // CLI loop
        def printHelp(): Unit = {
          println(
            "Commands:\n  :help          Show this help\n  :ports         List ports\n  :quit          Exit\n  :sendfft       Send complex numbers to FFT and compare with Breeze\n"
          )
        }

        printHelp()
        print("> ")
        try {
          while (running) {
            val line = StdIn.readLine()
            if (line == null) {
              running = false
            } else line.trim match {
              case ":quit" => running = false
              case ":help" => printHelp(); print("> ")
              case ":ports" =>
                listPorts().zipWithIndex.foreach { case (p, i) => println(s"  [$i] ${p.getSystemPortName} - ${p.getDescriptivePortName}") }
                print("> ")

              case s if s.startsWith(":sendfft") =>
                // Syntax: :sendfft <width> <binaryPoint> <n> <r1,i1> <r2,i2> ...
                val parts = s.stripPrefix(":sendfft").trim.split("[ ]+").filter(_.nonEmpty)
                if (parts.length < 4) {
                  println("Invalid :sendfft usage")
                  println(":sendfft <width> <binaryPoint> <n> <r1,i1> <r2,i2> ...")
                  print("> ")
                } else {
                  try {
                    val width = parts(0).toInt
                    val binaryPoint = parts(1).toInt
                    val n = parts(2).toInt
                    val pairs = parts.drop(3).map(_.trim).filter(_.nonEmpty)
                    if (pairs.length != n) println(s"Warning: provided ${pairs.length} pairs but n=$n")
                    val inputs = pairs.map { p =>
                      val pp = p.split(",")
                      (pp(0).toDouble, pp(1).toDouble)
                    }.toSeq
                    println(s"Sending ${inputs.length} complex numbers to FFT (width=$width, binaryPoint=$binaryPoint, n=$n)")
                    val chunks = ChunkUtils.getChunksForComplexNumbers(inputs, 8, width, binaryPoint)
                    val bytes = chunks.map(_.toByte).toArray
                    port.getOutputStream.write(bytes)
                    port.getOutputStream.flush()
                    println(s"Sent ${bytes.length} bytes to device, awaiting response...")

                    val expectedRespLen = bytes.length // expect same number of bytes back
                    val resp = readNBytes(expectedRespLen, 5000)
                    if (resp.length < expectedRespLen) {
                      println(s"Timed out waiting for response: got ${resp.length} bytes, expected $expectedRespLen")
                    } else {
                      println(s"Received ${resp.length} bytes")
                      val respInts = resp.map(b => (b & 0xff)).toSeq
                      val outputs = ChunkUtils.getComplexNumbersFromChunks(respInts, 8, width, binaryPoint)

                      val expectedOpt = ScalaFFTVerifier.verifyNPointFFT(inputs)
                      expectedOpt match {
                        case Some(expected) =>
                          val tolerance = FixedPointUtils.calculateToleranceDouble(n, width, binaryPoint)
                          println(f"Comparing FFT output against Breeze expected values with tolerance $tolerance%.6f")
                          val comparisons = expected.zip(outputs).zipWithIndex.map { case ((exp, out), idx) =>
                            val realDiff = (out._1 - exp._1).abs
                            val imagDiff = (out._2 - exp._2).abs
                            val realOk = realDiff <= tolerance
                            val imagOk = imagDiff <= tolerance
                            println(f"Index $idx: expected=(${exp._1}%.6f, ${exp._2}%.6f), got=(${out._1}%.6f, ${out._2}%.6f), realDiff=${realDiff}%.6f imagDiff=${imagDiff}%.6f ok=($realOk,$imagOk)")
                            realOk && imagOk
                          }
                          if (comparisons.forall(_ == true)) println("FFT response matches Breeze expected within tolerance.") else println("FFT response differs from Breeze expected!")
                        case None => println("Could not compute expected FFT via Breeze; skipping comparison.")
                      }
                    }
                  } catch {
                    case t: Throwable => println(s":sendfft parse/IO error: ${t.getMessage}")
                  }
                  print("> ")
                }
              case "" => print("> ")
              case other => println(s"Unknown command: $other"); print("> ")
            }
          }
        } finally {
          running = false
          try port.closePort() catch { case _: Throwable => () }
          println("Port closed. Bye.")
        }
      }
  }
}
