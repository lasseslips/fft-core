import chisel3._
import chisel3.util._

class Comparator(val width: Int, val binaryPoint: Int, val tolerance: Int, val pipeline: Boolean = false) extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in0 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        val in1 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))

        // Output indicating equality
        val equal = Output(Bool())
    })

    // Compute differences
    val diffReal = io.in0.real - io.in1.real
    val diffImag = io.in0.imag - io.in1.imag
    
    val absDiffReal = Mux(diffReal(width-1), -diffReal, diffReal)
    val absDiffImag = Mux(diffImag(width-1), -diffImag, diffImag)
    
    if (pipeline) {
        io.equal := RegNext((absDiffReal <= tolerance.S) && (absDiffImag <= tolerance.S))
    } else {
        io.equal := (absDiffReal <= tolerance.S) && (absDiffImag <= tolerance.S)
    }
}