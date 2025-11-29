import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import utils.FixedPointUtils
import verifier.FFTTestCase
import verifier.ScalaFFTVerifier
import utils.ChunkUtils

class BufferedFFTSpec extends AnyFlatSpec with ChiselScalatestTester {


  def printFailing(expected: Seq[(Double, Double)], outputs: Seq[(Double, Double)], tolerance : Double, comparisons: Seq[(Boolean, Boolean)]): Unit = {
    println("Mismatch detected!")
    println(s"Output complex numbers: $outputs")
    println(s"Expected complex numbers: $expected")

    // Print detailed comparison results
    for (i <- expected.indices) {
      val (expR, expI) = expected(i)
      val (outR, outI) = outputs(i)
      val (realMatch, imagMatch) = comparisons(i)
      val realDiff = (outR - expR).abs
      val imagDiff = (outI - expI).abs
      if (!realMatch || !imagMatch) {
        println(f"Index $i: Expected ($expR%.6f, $expI%.6f), Got ($outR%.6f, $outI%.6f), " +
          f"Diff Real: $realDiff%.6f, Diff Imag: $imagDiff%.6f, " +
          s"Tolerance: $tolerance, " +
          s"Real Match: $realMatch, Imag Match: $imagMatch")
      }
    }
    
  }

  def process(dut: BufferedFFT, inputs: Seq[(Double, Double)], expectedOutputs: Seq[(Double, Double)], toPrint: Boolean = false, index: Int): Unit = {
    val chunks = ChunkUtils.getChunksForComplexNumbers(inputs, 8, dut.width, dut.binaryPoint)
    val expectedOutputChunks = ChunkUtils.getChunksForComplexNumbers(expectedOutputs, 8, dut.width, dut.binaryPoint)

    dut.io.out.ready.poke(true.B)

    // Feed input chunks
    for (chunk <- chunks) {
      dut.io.in.valid.poke(true.B)
      if (toPrint) println(s"Sending chunk: $chunk")
      dut.io.in.bits.poke(chunk.U)
      while (!dut.io.in.ready.peek().litToBoolean) {
        dut.clock.step()
      }
      dut.clock.step()
    }

    dut.io.in.valid.poke(false.B)

    // Collect output chunks
    var received = Seq.empty[Int]
    while (received.size < chunks.size) {
      if (dut.io.out.valid.peek().litToBoolean) {
        val v = dut.io.out.bits.peek().litValue.toInt
        received = received :+ v
        if (toPrint) println(s"Received chunk: $v (expected ${expectedOutputChunks(received.size - 1)})")
      }
      dut.clock.step()
    }

    val receivedComplex = ChunkUtils.getComplexNumbersFromChunks(received, 8, dut.width, dut.binaryPoint)
    val tolerance = FixedPointUtils.calculateTolerance(dut.n, dut.width, dut.binaryPoint)
    val comparisons = expectedOutputs.zip(receivedComplex).map { case ((expR, expI), (outR, outI)) =>
      val realDiff = (outR - expR).abs
      val imagDiff = (outI - expI).abs
      (realDiff <= tolerance, imagDiff <= tolerance)
    }
    val allMatch = comparisons.forall { case (realMatch, imagMatch) => realMatch && imagMatch }
    if (!allMatch) {
      printFailing(expectedOutputs, receivedComplex, tolerance, comparisons)
      assert(false, s"Test case $index failed: Output does not match expected values within tolerance.")
    }
  }

  behavior of "BufferedFFT"

  it should "process a small vector end-to-end with pipeline = true" in {
    val width = 16
    val binaryPoint = 8
    val n = 4
    val pipeline = PipelineConfig(true, true, true)

    test(new BufferedFFT(n, width, binaryPoint, pipeline)) { dut =>
      // Prepare a simple input vector of complex numbers to send
      val inputs = Seq((1.0, 0.0), (2.0, -1.0), (0.5, 0.5), (-1.0, 1.0))
      val expectedOutputs = ScalaFFTVerifier.verifyNPointFFT(inputs) match {
        case Some(result) if result.length == n => result
        case _ => fail("Failed to get expected FFT outputs from Breeze")
      }
      process(dut, inputs, expectedOutputs.toList, true, 0)
    }
  }

  it should "process a small vector end-to-end with pipeline = false" in {
    val width = 16
    val binaryPoint = 8
    val n = 4
    val pipeline = PipelineConfig(false, false, false)

    test(new BufferedFFT(n, width, binaryPoint, pipeline)) { dut =>
      // Prepare a simple input vector of complex numbers to send
      val inputs = Seq((1.0, 0.0), (2.0, -1.0), (0.5, 0.5), (-1.0, 1.0))
      val expectedOutputs = ScalaFFTVerifier.verifyNPointFFT(inputs) match {
        case Some(result) if result.length == n => result
        case _ => fail("Failed to get expected FFT outputs from Breeze")
      }
      process(dut, inputs, expectedOutputs.toList, true, 0)
    }
  }

  it should "handle 100 random inputs correctly" in {
    val rand = new scala.util.Random(42)
    val width = 16
    val binaryPoint = 8
    //val n = Math.pow(2, rand.nextInt(4) + 2).toInt // n = 4, 8, 16, or 32
    val n = 8
    val pipeline = PipelineConfig(true, true, true)

    test(new BufferedFFT(n, width, binaryPoint, pipeline)) { dut =>
      for (i <- 0 until 100) {
        val inputs = Seq.fill(n) {
          val real = rand.nextDouble() * 4.0 - 2.0 // Range [-2.0, 2.0)
          val imag = rand.nextDouble() * 4.0 - 2.0
          (real, imag)
        }

        val expectedOutputs = ScalaFFTVerifier.verifyNPointFFT(inputs) match {
          case Some(result) if result.length == n => result
          case _ => fail("Failed to get expected FFT outputs from Breeze")
        }

        process(dut, inputs, expectedOutputs.toList, false, i)
      }
    }
  }


}
