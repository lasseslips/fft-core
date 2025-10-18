import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MemorySpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "ROM"

  it should "read correct values from ROM" in {
    val init = Seq(BigInt(1), BigInt(2), BigInt(3), BigInt(4))
    test(new ROM(depth = 4, width = 8, init = init)) { dut =>
      for (i <- init.indices) {
        dut.io.addr.poke(i.U)
        dut.clock.step()
        dut.io.data.expect(init(i).U)
      }
    }
  }

  behavior of "Memory"

  it should "write and read back values correctly" in {
    test(new Memory(depth = 8, width = 8)) { dut =>
      for (i <- 0 until 4) {
        dut.io.writeEnable.poke(true.B)
        dut.io.writeAddr.poke(i.U)
        dut.io.writeData.poke((i * 10).U)
        dut.clock.step()
      }

      dut.io.writeEnable.poke(false.B)
      for (i <- 0 until 4) {
        dut.io.readAddr.poke(i.U)
        dut.clock.step()
        dut.io.readData.expect((i * 10).U)
      }
    }
  }

  behavior of "ComplexROM"

  it should "read correct complex values from ROM" in {
    // (real, imag)
    val init = Seq(
      (BigInt(1), BigInt(4)),
      (BigInt(2), BigInt(5)),
      (BigInt(3), BigInt(6))
    )
    test(new ComplexROM(depth = 3, width = 8, binaryPoint = 4, init = init)) { dut =>
      for (i <- init.indices) {
        dut.io.addr.poke(i.U)
        dut.clock.step()
        dut.io.data.real.expect(init(i)._1.S)
        dut.io.data.imag.expect(init(i)._2.S)
      }
    }
  }

  behavior of "ComplexMemory"

  it should "write and read back complex values correctly" in {
    test(new ComplexMemory(depth = 4, width = 8, binaryPoint = 4)) { dut =>
      def pokeComplex(real: Int, imag: Int, addr: Int): Unit = {
        dut.io.writeEnable.poke(true.B)
        dut.io.writeAddr.poke(addr.U)
        dut.io.writeData.real.poke(real.S)
        dut.io.writeData.imag.poke(imag.S)
        dut.clock.step()
      }

      pokeComplex(10, -10, 0)
      pokeComplex(5, -7, 1)
      pokeComplex(3, -9, 2)

      dut.io.writeEnable.poke(false.B)
      for ((addr, (r, i)) <- Seq(
        (0, (10, -10)),
        (1, (5, -7)),
        (2, (3, -9))
      )) {
        dut.io.readAddr.poke(addr.U)
        dut.clock.step()
        dut.io.readData.real.expect(r.S)
        dut.io.readData.imag.expect(i.S)
      }
    }
  }
}
