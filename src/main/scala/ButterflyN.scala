import chisel3._
import chisel3.util._
import scala.math._

class ButterflyN(val n: Int, val width: Int, val binaryPoint: Int) extends Module {
    def isPow2(x: Int): Boolean = (x & (x - 1)) == 0
    require(n >= 2 && isPow2(n), "N must be a power of 2 and >= 2")
    
    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))

        // Output complex numbers
        val out = Output(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    val halfN = n / 2
    if (n == 2) {
        // Base case
        val butterfly = Module(new Butterfly2(width, binaryPoint))
        butterfly.io.in0 := io.in(0)
        butterfly.io.in1 := io.in(1)
        io.out(0) := butterfly.io.out0
        io.out(1) := butterfly.io.out1
    } else {
        val butterfly1 = VecInit(Seq.fill(halfN)(Module(new Butterfly(width, binaryPoint)).io))

        for (i <- 0 until halfN) {
            val angle = -2.0 * Pi * i / n
            val cosVal = cos(angle)
            val sinVal = sin(angle)
            val twiddleReal = FixedPointUtils.doubleToFixedPoint(cosVal, width, binaryPoint).S
            val twiddleImag = FixedPointUtils.doubleToFixedPoint(sinVal, width, binaryPoint).S

            butterfly1(i).in0 := io.in(i)
            butterfly1(i).in1 := io.in(i + halfN )
            butterfly1(i).twiddle.real := twiddleReal
            butterfly1(i).twiddle.imag := twiddleImag
        }

        // Second stage: two 4-point FFTs
        val butterfly00 = Module(new ButterflyN(halfN, width, binaryPoint))
        val butterfly01 = Module(new ButterflyN(halfN, width, binaryPoint))

        // Connect the first halfN-point FFT (even outputs)
        for (i <- 0 until halfN) {
            butterfly00.io.in(i) := butterfly1(i).out0
            butterfly01.io.in(i) := butterfly1(i).out1
        }

        // Connect outputs
        for (i <- 0 until halfN) {
            io.out(2*i) := butterfly00.io.out(i)
            io.out(2*i + 1) := butterfly01.io.out(i)
        }
    }
}


