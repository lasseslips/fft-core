import chisel3._
import scala.math._

// General FFT test data generator
object FFTTestData {
    
    // Generate test cases for different scenarios
    def generateTestCase(fftSize: Int, testType: String, width: Int = 16, binaryPoint: Int = 8): FFTTestCase = {
        val testInputs = testType.toLowerCase match {
            case "impulse" => generateImpulse(fftSize)
            case "sinusoid" => generateSinusoid(fftSize, 1) // frequency bin 1
            case "real_sin" => generateRealSinusoid(fftSize, 1)
            case "complex_exp" => generateComplexExponential(fftSize, 2)
            case "dc" => generateDC(fftSize)
            case "random" => generateRandom(fftSize)
            case _ => generateImpulse(fftSize) // default to impulse
        }
        
        // Convert to fixed point
        val inputFixed = testInputs.map { case (real, imag) =>
            val realFixed = FixedPointUtils.doubleToFixedPointUnsigned(real, width, binaryPoint)
            val imagFixed = FixedPointUtils.doubleToFixedPointUnsigned(imag, width, binaryPoint)
            (realFixed, imagFixed)
        }
        
        // Try to get expected results from Python verifier
        val expectedFixed = if (ScalaFFTVerifier.isBreezeAvailable) {
            ScalaFFTVerifier.verifyNPointFFT(testInputs) match {
                case Some(breezeResults) =>
                    breezeResults.map { case (real, imag) =>
                        val realFixed = FixedPointUtils.doubleToFixedPointUnsigned(real, width, binaryPoint)
                        val imagFixed = FixedPointUtils.doubleToFixedPointUnsigned(imag, width, binaryPoint)
                        (realFixed, imagFixed)
                    }
                case None =>
                    println("Warning: Breeze FFT verification failed, using input data as expected output.")
                    inputFixed
            }
        } else {
            println("Warning: Python not available, using input data as expected output.")
            inputFixed
        }
        
        // Calculate appropriate tolerance
        val tolerance = FixedPointUtils.calculateTolerance(fftSize, width, binaryPoint)
        
        FFTTestCase(
            name = testType,
            size = fftSize,
            inputData = inputFixed,
            expectedData = expectedFixed,
            tolerance = tolerance,
            width = width,
            binaryPoint = binaryPoint
        )
    }
    
    // Generate impulse signal: [1, 0, 0, ...]
    private def generateImpulse(size: Int): Seq[(Double, Double)] = {
        (0 until size).map { i =>
            if (i == 0) (1.0, 0.0) else (0.0, 0.0)
        }
    }
    
    // Generate complex sinusoid: exp(j*2*pi*k*n/N)
    private def generateSinusoid(size: Int, freqBin: Int): Seq[(Double, Double)] = {
        (0 until size).map { n =>
            val angle = 2.0 * Pi * freqBin * n / size
            (cos(angle), sin(angle))
        }
    }
    
    // Generate real sinusoid: sin(2*pi*k*n/N)
    private def generateRealSinusoid(size: Int, freqBin: Int): Seq[(Double, Double)] = {
        (0 until size).map { n =>
            val angle = 2.0 * Pi * freqBin * n / size
            (sin(angle), 0.0)
        }
    }
    
    // Generate complex exponential
    private def generateComplexExponential(size: Int, freqBin: Int): Seq[(Double, Double)] = {
        (0 until size).map { n =>
            val angle = 2.0 * Pi * freqBin * n / size
            (cos(angle), sin(angle))
        }
    }
    
    // Generate DC signal: [1, 1, 1, ...]
    private def generateDC(size: Int): Seq[(Double, Double)] = {
        Seq.fill(size)((1.0, 0.0))
    }
    
    // Generate random signal
    private def generateRandom(size: Int): Seq[(Double, Double)] = {
        val random = new scala.util.Random(42) // Fixed seed for reproducible tests
        (0 until size).map { _ =>
            (random.nextGaussian() * 0.5, random.nextGaussian() * 0.5)
        }
    }
}

// Case class to hold test data
case class FFTTestCase(
    name: String,
    size: Int,
    inputData: Seq[(BigInt, BigInt)],
    expectedData: Seq[(BigInt, BigInt)],
    tolerance: Int,
    width: Int,
    binaryPoint: Int
) {
    // Convert to format suitable for ROM initialization
    def getInputROMData: Seq[(BigInt, BigInt)] = inputData
    def getExpectedROMData: Seq[(BigInt, BigInt)] = expectedData
    
    
    // Debug information
    def printTestInfo(): Unit = {
        println(s"FFT Test Case: $name")
        println(s"Size: $size, Width: $width, Binary Point: $binaryPoint")
        println(s"Tolerance: $tolerance")
        println("Input data (fixed point):")
        inputData.zipWithIndex.foreach { case ((real, imag), i) =>
            val realDouble = FixedPointUtils.fixedPointToDouble(real, width, binaryPoint)
            val imagDouble = FixedPointUtils.fixedPointToDouble(imag, width, binaryPoint)
            println(f"  [$i]: ($realDouble%.6f, $imagDouble%.6f) -> ($real, $imag)")
        }
        println("Expected data (fixed point):")
        expectedData.zipWithIndex.foreach { case ((real, imag), i) =>
            val realDouble = FixedPointUtils.fixedPointToDouble(real, width, binaryPoint)
            val imagDouble = FixedPointUtils.fixedPointToDouble(imag, width, binaryPoint)
            println(f"  [$i]: ($realDouble%.6f, $imagDouble%.6f) -> ($real, $imag)")
        }
    }
}
