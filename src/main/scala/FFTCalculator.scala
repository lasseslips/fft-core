import chisel3._
import chisel3.util._
import scala.math._

/*
class FFTCalculator(val n: Int, val width: Int, val binaryPoint: Int) extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
        
        // Output complex numbers
        val out = Output(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    val butterflyN = Module(new ButterflyN(n, width, binaryPoint))

    butterflyN.io.in := io.in
    
    // Generate twiddle factors for the FFT
    for (k <- 0 until OtherUtils.calculateNumberOfTwiddles(n)) {
        val angle = -2.0 * Pi * k / n
        val cosVal = cos(angle)
        val sinVal = sin(angle)
        println(f"FFT_$n: Twiddle factor W_$n^$k: cos=$cosVal%.4f, sin=$sinVal%.4f")
        butterflyN.io.twiddle(k).real := FixedPointUtils.doubleToFixedPoint(cosVal, width, binaryPoint).S
        butterflyN.io.twiddle(k).imag := FixedPointUtils.doubleToFixedPoint(sinVal, width, binaryPoint).S
    }
    
    io.out := butterflyN.io.out
}
*/