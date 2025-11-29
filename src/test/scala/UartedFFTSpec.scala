import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import utils.FixedPointUtils
import verifier.ScalaFFTVerifier
import utils.ChunkUtils

class UartedFFTSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "UartedFFT"

  it should "process a small vector end-to-end" in {
    val baudRate = 1
    val clockFreq = 100
    val communicationWidth = 8
    val width = 16
    val binaryPoint = 8
    val n = 2
    val pipeline = PipelineConfig(
      pipelineComplexMultiplication = false,
      pipelineButterflyFirstPart = false,
      pipelineButterflySecondPart = false
    )
    val architecture = "GS"

    test(new UartedFFT(baudRate, clockFreq, width, binaryPoint, n, pipeline, architecture)) { dut =>
      // Set timeout higher than default for UART test
      dut.clock.setTimeout(1000000)
      dut.io.rts.poke(true.B)

      // Prepare a simple input vector of complex numbers to send via the RX UART
      val inputs = Seq((1.0, 0.0), (0.0, 0.0))

      // Build byte chunks (real then imag per complex) as the transmitter/receiver expect
      val chunks = ChunkUtils.getChunksForComplexNumbers(inputs, 8, dut.width, dut.binaryPoint)

      // Convert to byte array for UART encoding
      val bytesToSend = chunks.map(_.toByte).toArray
      val bitString = communication.UartCoding.encodeBytesToUartBits(bytesToSend)

      // Use small baud/clock values (match UART specs): keep clocksPerBaud small
      val clocksPerBaud = clockFreq / baudRate

      // Idle rx high for a few cycles
      dut.io.rx.poke(true.B)
      for (_ <- 0 until clocksPerBaud * 2) dut.clock.step()

      // Collect TX serial bits from the top-level `tx` line while sending and afterward
      val txBits = scala.collection.mutable.ArrayBuffer.empty[BigInt]

      // Drive the `rx` line according to the encoded UART bit string and sample `tx` each cycle
      for (ch <- bitString) {
        val bit = if (ch == '1') true.B else false.B
        dut.io.rx.poke(bit)
        // hold the bit for `clocksPerBaud` clock cycles and sample `tx` line while we wait
        for (_ <- 0 until clocksPerBaud) {
          txBits += (if (dut.io.tx.peek().litToBoolean) BigInt(1) else BigInt(0))
          dut.clock.step()
        }
      }

      println(s"Sent ${bytesToSend.length} bytes, received ${txBits.length / clocksPerBaud} bits over UART")

      // Release rx line to idle and collect additional cycles to finish transmission
      dut.io.rx.poke(true.B)
      val remainingCycles = communication.UartCoding.getCyclesNeededForBytes(clockFreq, baudRate, bytesToSend.length)
      for (_ <- 0 until remainingCycles) {
        txBits += (if (dut.io.tx.peek().litToBoolean) BigInt(1) else BigInt(0))
        dut.clock.step()
      }

      println(s"Total collected TX bits: ${txBits.length}")

      // Sample tx bits at baud rate to get the actual transmitted bits
      val sampledTxBits = txBits.grouped(clocksPerBaud).map { group =>
        if (group.count(_ == BigInt(1)) > group.length / 2) BigInt(1) else BigInt(0)
      }.toArray

      // Decode the serial bit stream into bytes
      val decoded = communication.UartCoding.decodeUartBitsToByteArray(sampledTxBits, communicationWidth)
      val expectedOutputChunks = ChunkUtils.getChunksForComplexNumbers(ScalaFFTVerifier.verifyNPointFFT(inputs).get.toSeq, 8, dut.width, dut.binaryPoint)
      val expectedOutputBytes = expectedOutputChunks.map(_.toByte).toArray
      assert(decoded.sameElements(expectedOutputBytes), s"Decoded output bytes do not match expected FFT output bytes.\nExpected: ${expectedOutputBytes.mkString(", ")}\nGot: ${decoded.mkString(", ")}")
    }
  }
}
