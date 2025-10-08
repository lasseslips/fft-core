import chisel3._

/**
 * An object extending App to generate the Verilog code.
 */
object Main extends App {
  println("I will now generate the Verilog file!")
  emitVerilog(new ButterflyN(16, 32, 16, true), Array("--target-dir", "verilog"))
}
