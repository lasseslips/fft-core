import chisel3._
import chisel3.util._

class BufferedFFT(
        val n : Int,
        val width: Int, 
        val binaryPoint: Int, 
        val pipeline : Boolean,
        val architecture: String = "GS"
    ) extends Module {

    require(width % 8 == 0, "Width must be a multiple of 8 to align with byte boundaries.")

    val io = IO(new Bundle {
        val in = Flipped(Decoupled(UInt(8.W)))
        val out = Decoupled(UInt(8.W))
    })

    val bytesPerElement = width / 8
    val totalBytes = n * 2 * bytesPerElement
    println(s"BufferedFFT: n=$n, width=$width, binaryPoint=$binaryPoint, bytesPerElement=$bytesPerElement, totalBytes=$totalBytes")

    val serializingBuffer = Module(new communication.SerializingByteBuffer(totalBytes))
    val deSerializingBuffer = Module(new communication.DeSerializingByteBuffer(totalBytes))

    val totalTwiddleCount = ButterflyNUtils.calcTwiddleCount(n)
    val twiddlesInt = ButterflyNUtils.generateTwiddleFactors(n)
    val twiddles = ButterflyNUtils.twiddlesToFixedPoint(twiddlesInt, width, binaryPoint)
    val interfacedFFT = Module(new InterfacedFFT(n, width, binaryPoint, pipeline, twiddles, architecture))

    // IO connections
    io.in <> deSerializingBuffer.io.inputChannel
    io.out <> serializingBuffer.io.outputChannel

    // FFT connections
    val complexInputs = Wire(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))
    val complexOutputs = Wire(Vec(n, new ComplexFixedPoint.Complex(width, binaryPoint)))

    interfacedFFT.io.in.bits := complexInputs
    interfacedFFT.io.in.valid := deSerializingBuffer.io.outputChannel.valid
    deSerializingBuffer.io.outputChannel.ready := interfacedFFT.io.in.ready

    complexOutputs := interfacedFFT.io.out.bits
    interfacedFFT.io.out.ready := serializingBuffer.io.inputChannel.ready
    serializingBuffer.io.inputChannel.valid := interfacedFFT.io.out.valid

    // Flatten complex inputs/outputs for serialization/deserialization
    for (i <- 0 until n) {
        // Each complex value (real/imag) may span multiple bytes.
        val realStart = i * 2 * bytesPerElement
        val imagStart = realStart + bytesPerElement

        // Reconstruct real value from bytes (assume little-endian: byte 0 is least-significant)
        val realBytes = Wire(Vec(bytesPerElement, UInt(8.W)))
        for (b <- 0 until bytesPerElement) {
            realBytes(b) := deSerializingBuffer.io.outputChannel.bits(realStart + b)
        }
        val realUInt = Cat(realBytes.reverse)
        complexInputs(i).real := realUInt.asSInt

        // Reconstruct imag value
        val imagBytes = Wire(Vec(bytesPerElement, UInt(8.W)))
        for (b <- 0 until bytesPerElement) {
            imagBytes(b) := deSerializingBuffer.io.outputChannel.bits(imagStart + b)
        }
        val imagUInt = Cat(imagBytes.reverse)
        complexInputs(i).imag := imagUInt.asSInt

        // Serialize outputs: split SInt into bytes (little-endian)
        val outRealUInt = complexOutputs(i).real.asUInt
        val outImagUInt = complexOutputs(i).imag.asUInt
        for (b <- 0 until bytesPerElement) {
            val byteReal = outRealUInt((b+1)*8-1, b*8)
            val byteImag = outImagUInt((b+1)*8-1, b*8)
            serializingBuffer.io.inputChannel.bits(realStart + b) := byteReal
            serializingBuffer.io.inputChannel.bits(imagStart + b) := byteImag
        }
    }


}