import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math._
import utils.FixedPointUtils
import verifier.FFTTestCase
import verifier.ScalaFFTVerifier

class Butterfly4Spec extends AnyFlatSpec with ChiselScalatestTester {
  def testButterfly4(dut : Butterfly4, width: Int, binaryPoint: Int, pipeline: PipelineConfig, architecture: String): Unit = {
    println("Testing DIF Butterfly4 against Scala Breeze 4-point FFT")
    if (pipeline.pipelineComplexMultiplication || pipeline.pipelineButterflyFirstPart || pipeline.pipelineButterflySecondPart) {
      assert(false, "Pipeline stages are not supported in this test. Please set all pipeline options to false.")
    }
    if (ScalaFFTVerifier.isBreezeAvailable) {
      println("Breeze detected - running FFT tests")
      
      val numTests = 10 
      val random = new scala.util.Random(42)
      val tolerance = 0.01
      
      for (testNum <- 1 to numTests) {
        println(f"\n--- Random Test Case $testNum/$numTests for 4-point Butterfly4 ---")
        
        // Generate random 4-point input
        val inputs = for (i <- 0 until 4) yield {
          val real = (random.nextDouble() - 0.5) * 2
          val imag = (random.nextDouble() - 0.5) * 2
          println(f"Input: x[$i] = $real%.3f + ${imag}%.3fj")
          (real, imag)
        }
        
        // Get breeze reference using 4-point FFT
        ScalaFFTVerifier.verifyNPointFFT(inputs) match {
          case Some(breezeResult) if breezeResult.length == 4 =>
            // Set hardware inputs
            for (i <- 0 until 4) {
              val (real, imag) = inputs(i)
              dut.io.in(i).real.poke(FixedPointUtils.doubleToFixedPoint(real, 16, 8).S)
              dut.io.in(i).imag.poke(FixedPointUtils.doubleToFixedPoint(imag, 16, 8).S)
            }

            dut.clock.step(1)

            // Get hardware outputs and compare with breeze results
            val errors = scala.collection.mutable.ListBuffer[(String, Double, Double, Double)]()
            
            for (i <- 0 until 4) {
              val act_real = FixedPointUtils.fixedPointToDouble(dut.io.out(i).real.peekInt(), 16, 8)
              val act_imag = FixedPointUtils.fixedPointToDouble(dut.io.out(i).imag.peekInt(), 16, 8)
              val (breeze_real, breeze_imag) = breezeResult(i)
              
              val real_error = abs(act_real - breeze_real)
              val imag_error = abs(act_imag - breeze_imag)
              
              errors += ((s"out$i.real", act_real, breeze_real, real_error))
              errors += ((s"out$i.imag", act_imag, breeze_imag, imag_error))
            }
            
            val errorList = errors.toList
            val maxError = errorList.map(_._4).max
            println(f"Max error: $maxError%.6f (tolerance: $tolerance)")
            
            errorList.foreach { case (name, hw, py, err) =>
              val status = if (err < tolerance) "PASS" else "FAIL"
              println(f"  $status $name: Breeze=$py%.6f, Hardware=$hw%.6f, Error=$err%.6f")
            }
            
            // Asserts to fail test if any error exceeds tolerance
            for ((name, hw, py, err) <- errorList) {
              assert(err < tolerance, 
                      f"Test case $testNum/$numTests FAILED on $name: Breeze=$py%.6f, Hardware=$hw%.6f, Error=$err%.6f > $tolerance")
            }
            
            println(s"Test case $testNum passed!")
            
          case Some(breezeResult) =>
            println(s"Breeze verification returned unexpected result length: ${breezeResult.length}, expected 4")
            fail(s"Test case $testNum/$numTests FAILED: Breeze FFT returned ${breezeResult.length} results instead of 4")
            
          case None =>
            println("Breeze verification failed - could not get results")
            fail(s"Test case $testNum/$numTests FAILED: Could not get Breeze FFT reference")
        }
      }
      
      println(f"\nAll $numTests random test cases passed for Butterfly4!")
      
    } else {
      println("Breeze not available - skipping this test")
      fail("Breeze not available")
    }
  }
  
  "Butterfly4" should "match Scala Breeze FFT results for 4-point FFT using DIF" in {
    test(new Butterfly4(16, 8, PipelineConfig(false, false, false), "GS")) { dut =>
      testButterfly4(dut, 16, 8, PipelineConfig(false, false, false), "GS")
    }
  }

  "Butterfly4" should "match Scala Breeze FFT results for 4-point FFT using DIT" in {
    test(new Butterfly4(16, 8, PipelineConfig(false, false, false), "CT")) { dut =>
      testButterfly4(dut, 16, 8, PipelineConfig(false, false, false), "CT")
    }
  }
}
