import chisel3._
import chisel3.util._
import scala.math._
import utils.FixedPointUtils

class ButterflyN(val n: Int, val width: Int, val binaryPoint: Int, val pipeline: PipelineConfig, val architecture: String = "GS") extends Module {
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
    if (architecture == "GS"){
        // First stage butterflies
        val butterflyS1 = VecInit(Seq.fill(halfN)(Module(new Butterfly(width, binaryPoint, pipeline, architecture)).io))
        
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
            val butterflyS2 = VecInit(Seq.fill(2)(Module(new ButterflyN(halfN, width, binaryPoint, pipeline, architecture)).io))

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
    } else if (architecture == "CT") {
        // Second stage butterflies
        val butterflyS2 = VecInit(Seq.fill(halfN)(Module(new Butterfly(width, binaryPoint, pipeline, architecture)).io))

        // Connect second stage butterflies
        for (i <- 0 until halfN) {
            io.out(i) := butterflyS2(i).out0
            io.out(i + halfN) := butterflyS2(i).out1
            butterflyS2(i).twiddle := io.twiddles(i)
        }

        if (n == 2) {
            // For N=2, directly connect inputs as there's no prior stage
            butterflyS2(0).in0 := io.in(0)
            butterflyS2(0).in1 := io.in(1)
        } else {
            // First stage: two halfN-point FFTs
            val butterflyS1 = VecInit(Seq.fill(2)(Module(new ButterflyN(halfN, width, binaryPoint, pipeline, architecture)).io))

            // Calculate twiddle factor distribution for recursive calls
            val recursiveTwiddleCount = ButterflyNUtils.calcTwiddleCount(halfN)

            // Connect inputs to recursive FFTs
            for (i <- 0 until halfN) {
                butterflyS1(0).in(i) := io.in(2*i)
                butterflyS1(1).in(i) := io.in(2*i + 1)
            }
            
            // Distribute remaining twiddle factors to recursive FFTs
            for (i <- 0 until recursiveTwiddleCount) {
                butterflyS1(0).twiddles(i) := io.twiddles(halfN + i)
                butterflyS1(1).twiddles(i) := io.twiddles(halfN + recursiveTwiddleCount + i)
            }

            // Connect outputs of first stage to second stage butterflies
            for (i <- 0 until halfN) {
                butterflyS2(i).in0 := butterflyS1(0).out(i)
                butterflyS2(i).in1 := butterflyS1(1).out(i)
            }
        }        
    } else {
        throw new Exception("Unsupported architecture type. Use 'CT' for Cooley-Tukey or 'GS' for Gentleman-Sande.")
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

    def getLatency(n: Int, pipeline: PipelineConfig): Int = {
        val stageLatency = {
            var latency = 0
            if (pipeline.pipelineComplexMultiplication) latency += 1
            if (pipeline.pipelineButterflyFirstPart) latency += 1
            if (pipeline.pipelineButterflySecondPart) latency += 1
            latency
        }
        if (n == 2) {
            stageLatency
        } else {
            val recursiveLatency = getLatency(n / 2, pipeline)
            stageLatency + recursiveLatency
        }
    }
}