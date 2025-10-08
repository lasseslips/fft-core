import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math._

class ButterflyNSpec extends AnyFlatSpec with ChiselScalatestTester {
  
  def testFFTWithSize(n: Int, width: Int, binaryPoint: Int, numTests: Int = 10): Unit = {
    test(new ButterflyN(n, width, binaryPoint)) { dut =>
      println(s"Testing ${n}-point FFT against Python NumPy FFT (if Python available)")
      
      if (PythonFFTVerifier.isPythonAvailable) {
        println("Python / NumPy detected - running FFT tests")
        
        val random = new scala.util.Random(42)
        val tolerance = 0.01
        
        for (testNum <- 1 to numTests) {
          println(f"\n--- Random Test Case $testNum/$numTests for ${n}-point FFT ---")
          
          // Generate random N-point input
          val inputs = for (i <- 0 until n) yield {
            val real = (random.nextDouble() - 0.5) * 2
            val imag = (random.nextDouble() - 0.5) * 2
            println(f"Input: x[$i] = $real%.3f + ${imag}%.3fj")
            (real, imag)
          }
          
          // Get Python reference using N-point FFT
          PythonFFTVerifier.verifyNPointFFTWithPython(inputs) match {
            case Some(pythonResult) if pythonResult.length == n =>
              // Set hardware inputs
              for (i <- 0 until n) {
                val (real, imag) = inputs(i)
                dut.io.in(i).real.poke(FixedPointUtils.doubleToFixedPoint(real, width, binaryPoint).S)
                dut.io.in(i).imag.poke(FixedPointUtils.doubleToFixedPoint(imag, width, binaryPoint).S)
              }

              dut.clock.step(1)

              // Get hardware outputs and compare with Python results
              val errors = scala.collection.mutable.ListBuffer[(String, Double, Double, Double)]()
              
              for (i <- 0 until n) {
                val act_real = FixedPointUtils.fixedPointToDouble(dut.io.out(i).real.peekInt(), width, binaryPoint)
                val act_imag = FixedPointUtils.fixedPointToDouble(dut.io.out(i).imag.peekInt(), width, binaryPoint)
                val (python_real, python_imag) = pythonResult(i)
                
                val real_error = abs(act_real - python_real)
                val imag_error = abs(act_imag - python_imag)
                
                errors += ((s"out$i.real", act_real, python_real, real_error))
                errors += ((s"out$i.imag", act_imag, python_imag, imag_error))
              }
              
              val errorList = errors.toList
              val maxError = errorList.map(_._4).max
              println(f"Max error: $maxError%.6f (tolerance: $tolerance%.6f)")
              
              errorList.foreach { case (name, hw, py, err) =>
                val status = if (err < tolerance) "PASS" else "FAIL"
                println(f"  $status $name: Python=$py%.6f, Hardware=$hw%.6f, Error=$err%.6f")
              }
              
              // Asserts to fail test if any error exceeds tolerance
              for ((name, hw, py, err) <- errorList) {
                assert(err < tolerance, 
                       f"Test case $testNum/$numTests FAILED on $name: Python=$py%.6f, Hardware=$hw%.6f, Error=$err%.6f > $tolerance")
              }
              
              println(s"Test case $testNum passed!")
              
            case Some(pythonResult) =>
              println(s"Python verification returned unexpected result length: ${pythonResult.length}, expected $n")
              fail(s"Test case $testNum/$numTests FAILED: Python FFT returned ${pythonResult.length} results instead of $n")
              
            case None =>
              println("Python verification failed - could not get results")
              fail(s"Test case $testNum/$numTests FAILED: Could not get Python FFT reference")
          }
        }
        
        println(f"\nAll $numTests random test cases passed for ${n}-point FFT!")
        
      } else {
        println("Python / NumPy not available - skipping this test")
        fail("Python / NumPy not available")
      }
    }
  }

 "ButterflyNSpec" should "match Python NumPy FFT results for 2-point FFT" in {
    testFFTWithSize(2, 16, 8, 10)
  }
  
  "ButterflyNSpec" should "match Python NumPy FFT results for 4-point FFT" in {
    testFFTWithSize(4, 16, 8, 10)
  }
  
  "ButterflyNSpec" should "match Python NumPy FFT results for 8-point FFT" in {
    testFFTWithSize(8, 16, 8, 10)
  }
  
  "ButterflyNSpec" should "match Python NumPy FFT results for 16-point FFT" in { // need larger precision to prevent error accumulation
    testFFTWithSize(16, 32, 16, 10)
  }
  
  
}

