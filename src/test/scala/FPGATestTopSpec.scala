import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import verifier.FFTTestCase
import verifier.FFTTestData

class FPGATestTopSpec extends AnyFlatSpec with ChiselScalatestTester {
    def testWithFFTSize(fftSize: Int, width: Int, binaryPoint: Int, pipeline: Boolean, testCases: Seq[FFTTestCase]): Unit = {
        test(new FPGATestTop(fftSize, width, binaryPoint, pipeline, testCases)) { dut =>
            println(s"Testing FPGATestTop with ${fftSize}-point FFT and ${testCases.length} test cases")

            val fftLatency = ButterflyNUtils.getLatency(fftSize, pipeline)
            val ifftLatency = fftLatency // IFFT wrapper uses same underlying latency
            val comparatorLatency = ComparatorUtils.getLatency(pipeline)
            // Exact expected cycles from entering the loop (includes sLoadInput, FFT, IFFT, compare and small control overhead)
            val expectedCycles = fftLatency + ifftLatency + comparatorLatency + 5
            println(s"Expected FFT latency: $fftLatency cycles, IFFT latency: $ifftLatency cycles, comparator latency: $comparatorLatency cycles, expected test cycles in loop: $expectedCycles cycles")
            val passStatus = scala.collection.mutable.ArrayBuffer.fill(testCases.length)(false)
            var cycle = 0
            for (i <- 0 until testCases.length) {
                // Start the test
                dut.io.startTest.poke(true.B)
                dut.clock.step(1)
                // Wait for test completion
                while (!dut.io.testComplete.peek().litToBoolean) {
                    dut.clock.step(1)
                    cycle += 1
                    assert(cycle <= expectedCycles, s"Test did not complete in expected time for test case $i")
                }
                println(s"Test completed in $cycle cycles")
                assert(cycle == expectedCycles, s"Test $i did not take the exact expected cycles: expected $expectedCycles but got $cycle")
                // Check LEDs for pass/fail indication
                val pass = dut.io.ledPass.peek().litToBoolean
                val fail = dut.io.ledFail.peek().litToBoolean
                assert(pass || fail, "Either pass or fail LED should be lit")
                assert(!(pass && fail), "Both pass and fail LEDs should not be lit simultaneously")
                if (pass) {
                    println(s"All values matched for test case $i")
                } else {
                    println(s"Some values did not match for test case $i")
                }
                assert(dut.io.testComplete.peek().litToBoolean, "Test did not complete in expected time")
                passStatus(i) = pass
                dut.io.startTest.poke(false.B) // Reset start signal 
                cycle = 0
                dut.clock.step(5) // Wait a few cycles before next test
            }

            // Final assertion: all tests should pass
            assert(passStatus.forall(_ == true), "Not all test cases passed")
            println("All test cases passed successfully!")
        }
    }

    "FPGATestTop" should "pass all test cases for 16-point FFT" in {
        val fftSize = 16
        val width = 16
        val binaryPoint = 8
        val pipeline = true
        val testCases = Seq(
            FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
        )
        testWithFFTSize(fftSize, width, binaryPoint, pipeline, testCases)
    }

    "FPGATestTop" should "pass all test cases for 8-point FFT" in {
        val fftSize = 8
        val width = 16
        val binaryPoint = 8
        val pipeline = true
        val testCases = Seq(
            FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
        )
        testWithFFTSize(fftSize, width, binaryPoint, pipeline, testCases)
    }

    "FPGATestTop" should "pass all test cases for 4-point FFT" in {
        val fftSize = 4
        val width = 16
        val binaryPoint = 8
        val pipeline = true
        val testCases = Seq(
            FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
        )
        testWithFFTSize(fftSize, width, binaryPoint, pipeline, testCases)
    }

    "FPGATestTop" should "pass all test cases for 2-point FFT" in {
        val fftSize = 2
        val width = 16
        val binaryPoint = 8
        val pipeline = true
        val testCases = Seq(
            FFTTestData.generateTestCase(fftSize, "impulse", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "sinusoid", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "real_sin", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "dc", width, binaryPoint),
            FFTTestData.generateTestCase(fftSize, "random", width, binaryPoint)
        )
        testWithFFTSize(fftSize, width, binaryPoint, pipeline, testCases)
    }

}