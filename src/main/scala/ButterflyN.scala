import chisel3._
import chisel3.util._
import scala.math._

class ButterflyN(val n: Int, val width: Int, val binaryPoint: Int, val pipeline: Boolean = false) extends Module {
    def isPow2(x: Int): Boolean = (x & (x - 1)) == 0
    require(n >= 2 && isPow2(n), "N must be a power of 2 and >= 2")
    
    val halfN = n / 2
    val totalTwiddleCount = ButterflyNUtils.calcTwiddleCount(n)

    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
        
        // All twiddle factors (up to halfN goes to first stage, rest to stage2 FFTs)
        val twiddles = Input(Vec(totalTwiddleCount, new ComplexFixedPoint.Complex(width, binaryPoint)))

        // Output complex numbers
        val out = Output(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    // First stage butterflies
    val butterflyS1 = VecInit(Seq.fill(halfN)(Module(new Butterfly(width, binaryPoint, pipeline)).io))
    
    // Connect first stage butterflies
    for (i <- 0 until halfN) {
        butterflyS1(i).in0 := io.in(i)
        butterflyS1(i).in1 := io.in(i + halfN)
        butterflyS1(i).twiddle := io.twiddles(i)
    }
    
    if (n == 2) {
        // For N=2, directly connect outputs as there's no further stage
        io.out(0) := butterflyS1(0).out0
        io.out(1) := butterflyS1(0).out1
    } else {
        // Second stage: two halfN-point FFTs
        val butterflyS2 = VecInit(Seq.fill(2)(Module(new ButterflyN(halfN, width, binaryPoint, pipeline)).io))

        // Calculate twiddle factor distribution for recursive calls
        val recursiveTwiddleCount = ButterflyNUtils.calcTwiddleCount(halfN)

        // Connect inputs to recursive FFTs
        for (i <- 0 until halfN) {
            butterflyS2(0).in(i) := butterflyS1(i).out0
            butterflyS2(1).in(i) := butterflyS1(i).out1
        }
        
        // Distribute remaining twiddle factors to recursive FFTs
        for (i <- 0 until recursiveTwiddleCount) {
            butterflyS2(0).twiddles(i) := io.twiddles(halfN + i)
            butterflyS2(1).twiddles(i) := io.twiddles(halfN + recursiveTwiddleCount + i)
        }

        // Connect outputs with bit-reversed order
        for (i <- 0 until halfN) {
            io.out(2*i) := butterflyS2(0).out(i)
            io.out(2*i + 1) := butterflyS2(1).out(i)
        }
    }
}

// Helper object for twiddle factor calculations
object ButterflyNUtils {
    def calcTwiddleCount(size: Int): Int = {
        if (size == 2) size / 2
        else size / 2 + 2 * calcTwiddleCount(size / 2)
    }

    def generateTwiddleFactors(size: Int): Seq[(Double, Double)] = {
        val halfSize = size / 2
        
        // Generate twiddle factors for this stage
        val stageTwiddles = for (i <- 0 until halfSize) yield {
            val angle = -2.0 * Pi * i / size
            (cos(angle), sin(angle))
        }
        
        if (size == 2) {
            stageTwiddles
        } else {
            // Recursively generate twiddles for sub-FFTs
            val subTwiddles = generateTwiddleFactors(halfSize)
            stageTwiddles ++ subTwiddles ++ subTwiddles
        }
    }
        
    def twiddlesToFixedPoint(twiddles: Seq[(Double, Double)], width: Int, binaryPoint: Int): Seq[(BigInt, BigInt)] = {
        twiddles.map { case (real, imag) =>
            val realFixed = FixedPointUtils.doubleToFixedPoint(real, width, binaryPoint)
            val imagFixed = FixedPointUtils.doubleToFixedPoint(imag, width, binaryPoint)
            (realFixed, imagFixed)
        }
    }

    // Remember to update this if pipeline stages in Butterfly change
    // TODO: as for pipelining. We currently have 2 cycles per stage (1 for sum/diff, 1 for mul)
    // Maybe we can adjust to 1 cycle in stages where twiddle is 1 (no mul needed)
    // Also look into pipelining inside multiplication (there are currently mapped 2 consecutive DSPs)
    // Currently we get around 150 MHz on Basys3 inside the Butterfly (with N=8, 16-bit width, 8-bit binary point)
    // The test harness surrounding the FFT is not pipelined optimally in the comparison so the overall freq is lower (around 104 MHz)
    def getLatency(n: Int, pipeline: Boolean): Int = {
        if (!pipeline) 0
        else 2*(log(n) / log(2)).toInt
    }
}