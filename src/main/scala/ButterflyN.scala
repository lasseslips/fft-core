import chisel3._
import chisel3.util._
import scala.math._

class ButterflyN(val n: Int, val width: Int, val binaryPoint: Int, val pipeline: Boolean = false) extends Module {
    def isPow2(x: Int): Boolean = (x & (x - 1)) == 0
    require(n >= 2 && isPow2(n), "N must be a power of 2 and >= 2")
    
    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))

        // Output complex numbers
        val out = Output(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    val halfN = n / 2

    // First stage
    val butterflyS1 = VecInit(Seq.fill(halfN)(Module(new Butterfly(width, binaryPoint, pipeline)).io))
    for (i <- 0 until halfN) {
        val angle = -2.0 * Pi * i / n
        val cosVal = cos(angle)
        val sinVal = sin(angle)
        val twiddleReal = FixedPointUtils.doubleToFixedPoint(cosVal, width, binaryPoint).S
        val twiddleImag = FixedPointUtils.doubleToFixedPoint(sinVal, width, binaryPoint).S
        butterflyS1(i).in0 := io.in(i)
        butterflyS1(i).in1 := io.in(i + halfN)
        butterflyS1(i).twiddle.real := twiddleReal
        butterflyS1(i).twiddle.imag := twiddleImag
    }
    if (n == 2) {
        // For N=2, directly connect outputs as there's no further stage
        io.out(0) := butterflyS1(0).out0
        io.out(1) := butterflyS1(0).out1
    } else {
        // Second stage: two halfN-point FFTs
        val butterflyS2 = VecInit(Seq.fill(2)(Module(new ButterflyN(halfN, width, binaryPoint, pipeline)).io))

        
        for (i <- 0 until halfN) {
            butterflyS2(0).in(i) := butterflyS1(i).out0
            butterflyS2(1).in(i) := butterflyS1(i).out1
        }

        // Connect outputs
        for (i <- 0 until halfN) {
            io.out(2*i) := butterflyS2(0).out(i)
            io.out(2*i + 1) := butterflyS2(1).out(i)
        }
    }
}


