import chisel3._
import chisel3.util._

class Butterfly(
    val width: Int, 
    val binaryPoint: Int, 
    val pipeline: PipelineConfig = PipelineConfig(false, false, false),
    val architecture : String = "GS"
    ) extends Module {
    val io = IO(new Bundle {
        // Input complex numbers
        val in0 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        val in1 = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        
        // Twiddle factor (complex exponential)
        val twiddle = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        
        // Output complex numbers
        val out0 = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
        val out1 = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
    })

    val out0 = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
    val out1 = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
    val stage10 = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
    val stage11 = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
    val stage20 = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))
    val stage21 = Wire(new ComplexFixedPoint.Complex(width, binaryPoint))

    if (architecture == "CT") {  // Cooley-Tukey Butterfly (DIT)
        // out0 = in0 + in1*twiddle
        // out1 = in0 - in1*twiddle
        if (pipeline.pipelineComplexMultiplication) {
            stage10 := RegNext(io.in0)
            stage11 := ComplexFixedPoint.mul(io.in1, io.twiddle, true)
        } else {
            stage10 := io.in0
            stage11 := ComplexFixedPoint.mul(io.in1, io.twiddle, false)
        }

        if(pipeline.pipelineButterflyFirstPart) {
            stage20 := RegNext(stage10)
            stage21 := RegNext(stage11)
        } else {
            stage20 := stage10
            stage21 := stage11
        }

        if(pipeline.pipelineButterflySecondPart) {
            out0 := RegNext(ComplexFixedPoint.add(stage20, stage21))
            out1 := RegNext(ComplexFixedPoint.sub(stage20, stage21))
        } else {
            out0 := ComplexFixedPoint.add(stage20, stage21)
            out1 := ComplexFixedPoint.sub(stage20, stage21)
        }
    } else if (architecture == "GS") {  // Gentleman-Sande Butterfly (DIF)
        // out0 = in0 + in1
        // out1 = (in0 - in1) * twiddle
        if (pipeline.pipelineButterflyFirstPart) {
            stage10 := RegNext(ComplexFixedPoint.add(io.in0, io.in1))
            stage11 := RegNext(ComplexFixedPoint.sub(io.in0, io.in1))
        } else {
            stage10 := ComplexFixedPoint.add(io.in0, io.in1)
            stage11 := ComplexFixedPoint.sub(io.in0, io.in1)
        }

        if (pipeline.pipelineComplexMultiplication) {
            stage20 := RegNext(stage10)
            stage21 := ComplexFixedPoint.mul(stage11, io.twiddle, true)
        } else {
            stage20 := stage10
            stage21 := ComplexFixedPoint.mul(stage11, io.twiddle, false)
        }

        if (pipeline.pipelineButterflySecondPart) {
            out0 := RegNext(stage20)
            out1 := RegNext(stage21)
        } else {
            out0 := stage20
            out1 := stage21
        }   
    } else {
        throw new Exception("Unsupported architecture type. Use 'CT' for Cooley-Tukey or 'GS' for Gentleman-Sande.")
    }

    io.out0 := out0
    io.out1 := out1
}
