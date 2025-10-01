import chisel3._

/**
 * An object extending App to generate the Verilog code.
 */
object Hello extends App {
  println("I will now generate the Verilog file for the Butterfly2 module")
  emitVerilog(new Butterfly2(16, 8))
}
