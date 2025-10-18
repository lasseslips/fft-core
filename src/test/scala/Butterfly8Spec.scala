import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import scala.math._

class Butterfly8Spec extends AnyFlatSpec with ChiselScalatestTester {

  "Butterfly8" should "match Scala Breeze FFT results for 8-point FFT" in {
    test(new Butterfly8(16, 8)) { dut =>
      println("Testing Butterfly8 against Scala Breeze 8-point FFT")

      if (ScalaFFTVerifier.isBreezeAvailable) {
        println("Breeze detected - running FFT tests")

        val numTests = 10
        val random = new scala.util.Random(42)
        val tolerance = 0.01

        for (testNum <- 1 to numTests) {
          println(f"\n--- Random Test Case $testNum/$numTests for 8-point Butterfly8 ---")

          // Generate random 8-point input
          val inputs = for (i <- 0 until 8) yield {
            val real = (random.nextDouble() - 0.5) * 2
            val imag = (random.nextDouble() - 0.5) * 2
            println(f"Input: x[$i] = $real%.3f + ${imag}%.3fj")
            (real, imag)
          }

          // Get Breeze reference using 8-point FFT
          ScalaFFTVerifier.verifyNPointFFT(inputs) match {
            case Some(breezeResult) if breezeResult.length == 8 =>
              // Set hardware inputs
              for (i <- 0 until 8) {
                val (real, imag) = inputs(i)
                dut.io.in(i).real.poke(FixedPointUtils.doubleToFixedPoint(real, 16, 8).S)
                dut.io.in(i).imag.poke(FixedPointUtils.doubleToFixedPoint(imag, 16, 8).S)
              }

              dut.clock.step(1)

              // Get hardware outputs and compare with Breeze results
              val errors = scala.collection.mutable.ListBuffer[(String, Double, Double, Double)]()
              
              for (i <- 0 until 8) {
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

              errorList.foreach { case (name, hw, ref, err) =>
                val status = if (err < tolerance) "PASS" else "FAIL"
                println(f"  $status $name: Breeze=$ref%.6f, Hardware=$hw%.6f, Error=$err%.6f")
              }

              // Assert all within tolerance
              for ((name, hw, ref, err) <- errorList) {
                assert(err < tolerance,
                  f"Test case $testNum/$numTests FAILED on $name: Breeze=$ref%.6f, Hardware=$hw%.6f, Error=$err%.6f > $tolerance")
              }

              println(s"Test case $testNum passed!")

            case Some(breezeResult) =>
              println(s"Breeze verification returned unexpected result length: ${breezeResult.length}, expected 8")
              fail(s"Test case $testNum/$numTests FAILED: Breeze FFT returned ${breezeResult.length} results instead of 8")

            case None =>
              println("Breeze verification failed - could not get results")
              fail(s"Test case $testNum/$numTests FAILED: Could not get Breeze FFT reference")
          }
        }

        println(f"\nAll $numTests random test cases passed for Butterfly8!")

      } else {
        println("Breeze not available - skipping this test")
        fail("Breeze not available")
      }
    }
  }
}
