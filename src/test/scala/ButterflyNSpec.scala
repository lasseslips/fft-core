import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math._

class ButterflyNSpec extends AnyFlatSpec with ChiselScalatestTester {
  def genNumStr(nums: Seq[(Double, Double)]): String = {
    nums.map { case (re, im) => f"(${re}%.5f,${im}%.5f)" }.mkString(", ")
  }

  def testFFTWithSize(n: Int, width: Int, binaryPoint: Int, numTests: Int = 5, pipeline: Boolean = false): Unit = {
    test(new ButterflyN(n, width, binaryPoint, pipeline)) { dut =>
      println(s"Testing ${n}-point FFT against original ButterflyN and Scala Breeze FFT")

      if (ScalaFFTVerifier.isBreezeAvailable) {
        println("Breeze detected - running FFT tests")

        val random = new scala.util.Random(42)
        val tolerance = 0.01
        val latency = ButterflyNUtils.getLatency(n, pipeline)

        // Generate twiddle factors
        val twiddleFactorsDouble = ButterflyNUtils.generateTwiddleFactors(n)
        val twiddleFactorsFixed = ButterflyNUtils.twiddlesToFixedPoint(twiddleFactorsDouble, width, binaryPoint)

        // Apply twiddle factors to all tests
        for (i <- 0 until twiddleFactorsFixed.length) {
          dut.io.twiddles(i).real.poke(twiddleFactorsFixed(i)._1.S)
          dut.io.twiddles(i).imag.poke(twiddleFactorsFixed(i)._2.S)
        }
        
        println(f"Generated ${twiddleFactorsFixed.length} twiddle factors for ${n}-point FFT")

        val inputsBuffer = scala.collection.mutable.ListBuffer[Seq[(Double, Double)]]()
        val outputsBuffer = scala.collection.mutable.ListBuffer[Seq[(Double, Double)]]()
        val breezeResults = scala.collection.mutable.ListBuffer[Seq[(Double, Double)]]()

        // Generate inputs to test
        for (testNum <- 1 to numTests) {
          val inputs = for (i <- 0 until n) yield {
            val real = (random.nextDouble() - 0.5) * 2
            val imag = (random.nextDouble() - 0.5) * 2
            (real, imag)
          }
          inputsBuffer += inputs
        }

        // Get Breeze reference using N-point FFT
        for (testNum <- 0 until numTests) {
          val inputs = inputsBuffer(testNum)
          ScalaFFTVerifier.verifyNPointFFT(inputs) match {
            case Some(breezeResult) if breezeResult.length == n =>
              breezeResults += breezeResult
            case Some(breezeResult) =>
              println(s"Error: Breeze FFT did not return expected number of results, expected $n but got ${breezeResult.length}")
              fail("Breeze FFT error")
            case _ =>
              println("Error: Breeze FFT did not return any results")
              fail("Breeze FFT error")
          }
        }
        println(s"Running ${numTests} tests with ${n}-point ButterflyNExternal (latency: $latency cycles)\n\n")
        
        // Run tests storing results in outputsBuffer (keep pipeline latency in mind)
        for (testNum <- 0 until numTests) {
          // Set hardware inputs
          val inputs = inputsBuffer(testNum)
          for (i <- 0 until n) {
            val (real, imag) = inputs(i)
            dut.io.in(i).real.poke(FixedPointUtils.doubleToFixedPoint(real, width, binaryPoint).S)
            dut.io.in(i).imag.poke(FixedPointUtils.doubleToFixedPoint(imag, width, binaryPoint).S)
          }
          println(s"Testing inputs $testNum: ${genNumStr(inputs)}")


          // Collect outputs
          val currentOutputs = for (i <- 0 until n) yield {
            val act_real = FixedPointUtils.fixedPointToDouble(dut.io.out(i).real.peekInt(), width, binaryPoint)
            val act_imag = FixedPointUtils.fixedPointToDouble(dut.io.out(i).imag.peekInt(), width, binaryPoint)
            (act_real, act_imag)
          }
          println(s"Outputs at cycle $testNum: ${genNumStr(currentOutputs)}")
          if (testNum >= latency) {
            println(s"Expected Result ${testNum - latency} (Breeze): ${genNumStr(breezeResults(testNum - latency))}")
          }
          outputsBuffer += currentOutputs
          dut.clock.step(1)
          println()
        }

        // Zero inputs to flush pipeline
        for (i <- 0 until n) {
          dut.io.in(i).real.poke(0.S)
          dut.io.in(i).imag.poke(0.S)
        }

        // If pipelined, step additional cycles to flush outputs
        for (flushCycle <- 0 until latency) {
          val currentOutputs = for (i <- 0 until n) yield {
            val act_real = FixedPointUtils.fixedPointToDouble(dut.io.out(i).real.peekInt(), width, binaryPoint)
            val act_imag = FixedPointUtils.fixedPointToDouble(dut.io.out(i).imag.peekInt(), width, binaryPoint)
            (act_real, act_imag)
          }
          println(s"Outputs at cycle ${numTests + flushCycle}: ${genNumStr(currentOutputs)}")
          if (numTests + flushCycle >= latency) {
            println(s"Expected Result ${numTests + flushCycle - latency} (Breeze): ${genNumStr(breezeResults(numTests + flushCycle - latency))}")
          }
          outputsBuffer += currentOutputs
          dut.clock.step(1)
          println()
        }

        // Compare hardware vs Breeze results
        for (testNum <- 0 until numTests) {
          println(f"\n--- Test Case ${testNum + 1}/$numTests for ${n}-point ButterflyNExternal ---")
          val outputResults = outputsBuffer(testNum + latency)
          val breezeResult = breezeResults(testNum)
          val errors = scala.collection.mutable.ListBuffer[(String, Double, Double, Double)]()

          for (i <- 0 until n) {
            val (act_real, act_imag) = outputResults(i)
            val (breeze_real, breeze_imag) = breezeResult(i)

            val real_error = abs(act_real - breeze_real)
            val imag_error = abs(act_imag - breeze_imag)

            errors += ((s"out$i.real", act_real, breeze_real, real_error))
            errors += ((s"out$i.imag", act_imag, breeze_imag, imag_error))
          }

          val errorList = errors.toList
          val maxError = errorList.map(_._4).max
          println(f"Max error: $maxError%.6f (tolerance: $tolerance)")

          errorList.foreach { case (name, hw, ref, err) =>
            val status = if (err < tolerance) "PASS" else "FAIL"
            println(f"  $status $name: Breeze=$ref%.6f, Hardware=$hw%.6f, Error=$err%.6f")
          }

          // Fail if any exceed tolerance
          for ((name, hw, ref, err) <- errorList) {
            assert(err < tolerance,
              f"Test case ${testNum + 1}/$numTests FAILED on $name: Breeze=$ref%.6f, Hardware=$hw%.6f, Error=$err%.6f > $tolerance")
          }
        }
      } else {
        println("Breeze not detected")
        fail("Breeze not available")
      }
    }
  }



  "ButterflyNSpec" should "match Breeze FFT results for 2-point FFT (non-pipelined)" in {
    testFFTWithSize(2, 16, 8, 5, false)
  }

  "ButterflyNSpec" should "match Breeze FFT results for 4-point FFT (non-pipelined)" in {
    testFFTWithSize(4, 16, 8, 5, false)
  }

  "ButterflyNSpec" should "match Breeze FFT results for 8-point FFT (non-pipelined)" in {
    testFFTWithSize(8, 16, 8, 5, false)
  }

  "ButterflyNSpec" should "match Breeze FFT results for 16-point FFT (non-pipelined)" in {
    testFFTWithSize(16, 32, 16, 3, false)
  }

  "ButterflyNSpec" should "match Breeze FFT results for 2-point FFT (pipelined)" in {
    testFFTWithSize(2, 16, 8, 5, true)
  }

  "ButterflyNSpec" should "match Breeze FFT results for 4-point FFT (pipelined)" in {
    testFFTWithSize(4, 16, 8, 5, true)
  }

  "ButterflyNSpec" should "match Breeze FFT results for 8-point FFT (pipelined)" in {
    testFFTWithSize(8, 16, 8, 5, true)
  }

  "ButterflyNSpec" should "match Breeze FFT results for 16-point FFT (pipelined)" in {
    testFFTWithSize(16, 32, 16, 3, true)
  }

}