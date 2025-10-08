import chisel3._
import chisel3.util._
import scala.math._
/*
class ButterflyN(val n: Int, val width: Int, val binaryPoint: Int) extends Module {
    require(n >= 2 && isPow2(n), "N must be a power of 2 and >= 2")
    val numTwiddles = OtherUtils.calculateNumberOfTwiddles(n)
    
    val io = IO(new Bundle {
        // Input complex numbers
        val in = Input(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
        
        // Twiddle factors
        val twiddle = Input(Vec(numTwiddles, new ComplexFixedPoint.Complex(width, binaryPoint)))

        // Output complex numbers
        val out = Output(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
    })

    // Check if n is a power of 2
    def isPow2(x: Int): Boolean = (x & (x - 1)) == 0

    if (n == 2) {
        // Base case: use Butterfly2 directly
        val bfly2 = Module(new Butterfly2(width, binaryPoint))
        bfly2.io.in0 := io.in(0)
        bfly2.io.in1 := io.in(1)
        bfly2.io.twiddle := io.twiddle(0)
        
        io.out(0) := bfly2.io.out0
        io.out(1) := bfly2.io.out1
    } else {
        // Recursive decomposition: use two ButterflyN modules of size N/2
        val halfN = n / 2
        
        // Create two ButterflyN modules for the first stage
        val bflyTop = Module(new ButterflyN(halfN, width, binaryPoint))
        val bflyBottom = Module(new ButterflyN(halfN, width, binaryPoint))
        
        // Connect inputs: even indices to top, odd indices to bottom
        for (i <- 0 until halfN) {
            bflyTop.io.in(i) := io.in(i * 2)
            bflyBottom.io.in(i) := io.in(i * 2 + 1)
        }
        
        // Connect twiddle factors for the recursive modules
        for (i <- 0 until OtherUtils.calculateNumberOfTwiddles(halfN)) {
            bflyTop.io.twiddle(i) := io.twiddle(i * 2)
            bflyBottom.io.twiddle(i) := io.twiddle(i * 2 + 1)
        }

        // Second stage: combine results with Butterfly2 operations
        for (i <- 0 until halfN) {
            val bfly2 = Module(new Butterfly2(width, binaryPoint))
            
            bfly2.io.in0 := bflyTop.io.out(i)
            bfly2.io.in1 := bflyBottom.io.out(i)
            
            bfly2.io.twiddle := io.twiddle(i)

            io.out(i) := bfly2.io.out0
            io.out(i + halfN) := bfly2.io.out1
        }
    }
}

*/