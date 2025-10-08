import chisel3._

/**
 * An object extending App to generate the Verilog code.
 */
object Main extends App {
  println("I will now generate the Verilog file!")
  emitVerilog(new Butterfly8(32, 16))
}
