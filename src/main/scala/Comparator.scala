import chisel3._
import chisel3.util._

class Comparator(val width: Int, val binaryPoint: Int, val tolerance: Int, val pipeline: PipelineConfig) extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in0 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        val in1 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))

        // Output indicating equality
        val equal = Output(Bool())
    })
    val pipelineBool = pipeline.pipelineComplexMultiplication || pipeline.pipelineButterflyFirstPart || pipeline.pipelineButterflySecondPart

    val diff = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
    val absDiff = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
    // Compute differences
    if (pipelineBool) {
        diff.real := RegNext(io.in0.real - io.in1.real)
        diff.imag := RegNext(io.in0.imag - io.in1.imag)
    } else {
        diff.real := io.in0.real - io.in1.real
        diff.imag := io.in0.imag - io.in1.imag
    }
    
    // Compute absolute values of differences
    absDiff.real := Mux(diff.real < 0.S, -diff.real, diff.real)
    absDiff.imag := Mux(diff.imag < 0.S, -diff.imag, diff.imag)
    
    if (pipelineBool) {
        io.equal := RegNext((absDiff.real <= tolerance.S) && (absDiff.imag <= tolerance.S))
    } else {
        io.equal := (absDiff.real <= tolerance.S) && (absDiff.imag <= tolerance.S)
    }
}

object ComparatorUtils {
    // Calculate the latency for the comparator
    def getLatency(pipeline: PipelineConfig): Int = {
        if (!(pipeline.pipelineComplexMultiplication || pipeline.pipelineButterflyFirstPart || pipeline.pipelineButterflySecondPart)) 0
        else 2 // one for calculation and one in output register
    }
}