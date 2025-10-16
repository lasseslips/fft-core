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
      println(s"Testing ${n}-point FFT against original ButterflyN and Python NumPy FFT")

      if (PythonFFTVerifier.isPythonAvailable) {
        println("Python / NumPy detected - running FFT tests")
        
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
        val pythonResults = scala.collection.mutable.ListBuffer[Seq[(Double, Double)]]()

        // Generate inputs to test
        for (testNum <- 1 to numTests) {
          val inputs = for (i <- 0 until n) yield {
            val real = (random.nextDouble() - 0.5) * 2
            val imag = (random.nextDouble() - 0.5) * 2
            (real, imag)
          }
          inputsBuffer += inputs
        }

        // Get Python reference using N-point FFT
        for (testNum <- 0 until numTests) {
          val inputs = inputsBuffer(testNum)
          PythonFFTVerifier.verifyNPointFFTWithPython(inputs) match {
            case Some(pythonResult) if pythonResult.length == n =>
              pythonResults += pythonResult
            case Some(pythonResult) =>
              println("Error: Python FFT did not return expected number of results, expected length: " + n + " but got " + 
                      (if (pythonResult != null) pythonResult.length.toString else "null"))
              fail("Python FFT error")
            case _ =>
              println("Error: Python FFT did not return any results")
              fail("Python FFT error")
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
            println(s"Expected Result ${testNum - latency} (Python): ${genNumStr(pythonResults(testNum - latency))}")
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
            println(s"Expected Result ${numTests + flushCycle - latency} (Python): ${genNumStr(pythonResults(numTests + flushCycle - latency))}")
          }
          outputsBuffer += currentOutputs
          dut.clock.step(1)
          println()
        }

        // Compare hardware outputs with Python results
        for (testNum <- 0 until numTests) {
          println(f"\n--- Test Case ${testNum + 1}/$numTests for ${n}-point ButterflyNExternal ---")
          val outputResults = outputsBuffer(testNum + latency)
          val pythonResult = pythonResults(testNum)
          val errors = scala.collection.mutable.ListBuffer[(String, Double, Double, Double)]()

          for (i <- 0 until n) {
            val (act_real, act_imag) = outputResults(i)
            val (python_real, python_imag) = pythonResult(i)

            val real_error = abs(act_real - python_real)
            val imag_error = abs(act_imag - python_imag)

            errors += ((s"out$i.real", act_real, python_real, real_error))
            errors += ((s"out$i.imag", act_imag, python_imag, imag_error))
          }

          val errorList = errors.toList
          val maxError = errorList.map(_._4).max
          println(f"Max error: $maxError%.6f (tolerance: $tolerance)")

          errorList.foreach { case (name, hw, py, err) =>
            val status = if (err < tolerance) "PASS" else "FAIL"
            println(f"  $status $name: Python=$py%.6f, Hardware=$hw%.6f, Error=$err%.6f")
          }

          // Asserts to fail test if any error exceeds tolerance
          for ((name, hw, py, err) <- errorList) {
            assert(err < tolerance, 
                   f"Test case ${testNum + 1}/$numTests FAILED on $name: Python=$py%.6f, Hardware=$hw%.6f, Error=$err%.6f > $tolerance")
          }
        }
      } else {
        println("Python / NumPy not detected")
        fail("Python / NumPy not available")
      }
    }
  }



  
  "ButterflyNSpec" should "match Python NumPy FFT results for 2-point FFT (non-pipelined)" in {
    testFFTWithSize(2, 16, 8, 5, false)
  }

  "ButterflyNSpec" should "match Python NumPy FFT results for 4-point FFT (non-pipelined)" in {
    testFFTWithSize(4, 16, 8, 5, false)
  }

  "ButterflyNSpec" should "match Python NumPy FFT results for 8-point FFT (non-pipelined)" in {
    testFFTWithSize(8, 16, 8, 5, false)
  }
  

  "ButterflyNSpec" should "match Python NumPy FFT results for 16-point FFT (non-pipelined)" in {
    testFFTWithSize(16, 32, 16, 3, false)
  }

  "ButterflyNSpec" should "match Python NumPy FFT results for 2-point FFT (pipelined)" in {
    testFFTWithSize(2, 16, 8, 5, true)
  }

  "ButterflyNSpec" should "match Python NumPy FFT results for 4-point FFT (pipelined)" in {
    testFFTWithSize(4, 16, 8, 5, true)
  }

  "ButterflyNSpec" should "match Python NumPy FFT results for 8-point FFT (pipelined)" in {
    testFFTWithSize(8, 16, 8, 5, true)
  }

  "ButterflyNSpec" should "match Python NumPy FFT results for 16-point FFT (pipelined)" in {
    testFFTWithSize(16, 32, 16, 3, true)
  }

}