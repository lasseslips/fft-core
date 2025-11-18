import chisel3._
import chisel3.util._

class InterfacedFFT(
    val n : Int,
    val width: Int, 
    val binaryPoint: Int, 
    val pipeline : Boolean,
    val twiddles: Seq[(BigInt, BigInt)]
    ) extends Module {

    val io = IO(new Bundle {
        val in = Flipped(Decoupled(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint))))
        val out = Decoupled(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    val fftCore = Module(new ButterflyN(n, width, binaryPoint, pipeline))

    for (idx <- twiddles.indices) {
        val real = twiddles(idx)._1
        val imag = twiddles(idx)._2
        fftCore.io.twiddles(idx).real := real.S(width.W)
        fftCore.io.twiddles(idx).imag := imag.S(width.W)
    }

    fftCore.io.in := io.in.bits

    // Control logic:
    val latency = ButterflyNUtils.getLatency(n, pipeline)

    val buffer = RegInit(VecInit(Seq.fill(n)(0.U.asTypeOf(new ComplexFixedPoint.Complex(width, binaryPoint)))))
    val timer = RegInit(0.U(log2Ceil(latency + 1).W))

    val idle :: calculating :: finished :: Nil = Enum(3)
    val state = RegInit(idle)

    io.in.ready := false.B
    io.out.valid := false.B
    io.out.bits := buffer

    switch(state) {
        is(idle) {
            io.in.ready := true.B
            if(latency == 0) { // Non-pipelined FFT with zero latency (we don't need to wait)
                when(io.in.valid && io.in.ready) {
                    state := finished
                    buffer := fftCore.io.out
                }
            } else {
                when(io.in.valid) {
                    state := calculating
                    timer := latency.U
                }
            }
        }
        is(calculating) {
            when(timer === 1.U) {
                state := finished
                buffer := fftCore.io.out
            }
            timer := timer - 1.U
        }
        is(finished) {
            io.out.valid := true.B
            when(io.out.ready) {
                state := idle
            }
        }
    }
}