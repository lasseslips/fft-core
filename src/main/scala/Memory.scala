import chisel3._
import chisel3.util._

class ROM(val depth: Int, val width: Int, init: Seq[BigInt]) extends Module {
    require(init.length == depth, s"Initialization sequence length ${init.length} does not match ROM depth $depth")
    val io = IO(new Bundle {
        val addr = Input(UInt(log2Ceil(depth).W))
        val data = Output(UInt(width.W))
    })

    val mem = VecInit(init.map(x => x.U(width.W)))

    io.data := mem(io.addr)
}

class Memory(val depth: Int, val width: Int) extends Module {
    val io = IO(new Bundle {
        val writeEnable = Input(Bool())
        val writeAddr   = Input(UInt(log2Ceil(depth).W))
        val writeData   = Input(UInt(width.W))
        val readAddr    = Input(UInt(log2Ceil(depth).W))
        val readData    = Output(UInt(width.W))
    })

    val mem = SyncReadMem(depth, UInt(width.W))

    when(io.writeEnable) {
        mem.write(io.writeAddr, io.writeData)
    }

    io.readData := mem.read(io.readAddr, !io.writeEnable)
}

class ComplexROM(val depth: Int, val width: Int, val binaryPoint: Int, init: Seq[(BigInt, BigInt)]) extends Module {
    require(init.length == depth, s"Initialization sequence length ${init.length} does not match ROM depth $depth")
    val io = IO(new Bundle {
        val addr = Input(UInt(log2Ceil(depth).W))
        val data = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
    })

    val realInit = init.map(_._1)
    val imagInit = init.map(_._2)

    val realROM = Module(new ROM(depth, width, realInit))
    val imagROM = Module(new ROM(depth, width, imagInit))

    realROM.io.addr := io.addr
    imagROM.io.addr := io.addr

    io.data.real := realROM.io.data.asSInt
    io.data.imag := imagROM.io.data.asSInt
}

class ComplexMemory(val depth: Int, val width: Int, val binaryPoint: Int) extends Module {
    val io = IO(new Bundle {
        val writeEnable = Input(Bool())
        val writeAddr   = Input(UInt(log2Ceil(depth).W))
        val writeData   = Input(new ComplexFixedPoint.Complex(width, binaryPoint))
        val readAddr    = Input(UInt(log2Ceil(depth).W))
        val readData    = Output(new ComplexFixedPoint.Complex(width, binaryPoint))
    })

    val realMem = Module(new Memory(depth, width))
    val imagMem = Module(new Memory(depth, width))

    realMem.io.writeEnable := io.writeEnable
    realMem.io.writeAddr   := io.writeAddr
    realMem.io.writeData   := io.writeData.real.asUInt
    realMem.io.readAddr    := io.readAddr

    imagMem.io.writeEnable := io.writeEnable
    imagMem.io.writeAddr   := io.writeAddr
    imagMem.io.writeData   := io.writeData.imag.asUInt
    imagMem.io.readAddr    := io.readAddr

    io.readData.real := realMem.io.readData.asSInt
    io.readData.imag := imagMem.io.readData.asSInt
}