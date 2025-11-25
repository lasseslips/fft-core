import chisel3._
import chisel3.util._

class UartedFFT(
    val baudRate: Int, 
    val clockFreq: Int, 
    val width: Int, 
    val binaryPoint: Int, 
    val n : Int,
    val pipeline : Boolean,
    val architecture: String
    ) extends Module {

    val io = IO(new Bundle {
        val rx = Input(Bool())
        val cts = Output(Bool())
        val tx = Output(Bool())
        val rts = Input(Bool())
    })

    val rxUart = Module(new communication.UartRx(clockFreq, baudRate))
    val txUart = Module(new communication.UartTx(clockFreq, baudRate))

    val fft = Module(new BufferedFFT(n, width, binaryPoint, pipeline, architecture))

    // Connect RX UART to Complex Receiver
    rxUart.io.rxd := io.rx
    io.cts := rxUart.io.cts
    fft.io.in <> rxUart.io.outputChannel
    
    // Connect Complex Transmitter to TX UART
    io.tx := txUart.io.txd
    txUart.io.rts := io.rts
    fft.io.out <> txUart.io.inputChannel

}