import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ComparatorSpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "Comparator"

  it should "detect equality correctly without pipeline" in {
    test(new Comparator(width = 16, binaryPoint = 8, tolerance = 1, pipeline = false)) { dut =>
      def toSFix(value: Double): SInt = {
        (value * (1 << dut.binaryPoint)).toInt.S
      }

      dut.io.in0.real.poke(toSFix(1.0))
      dut.io.in0.imag.poke(toSFix(-0.5))
      dut.io.in1.real.poke(toSFix(1.0))
      dut.io.in1.imag.poke(toSFix(-0.5))
      dut.clock.step()
      dut.io.equal.expect(true.B)

      dut.io.in0.real.poke(toSFix(1.0))
      dut.io.in1.real.poke(toSFix(1.003))
      dut.io.in0.imag.poke(toSFix(-0.5))
      dut.io.in1.imag.poke(toSFix(-0.498))
      dut.clock.step()
      dut.io.equal.expect(true.B)

      dut.io.in0.real.poke(toSFix(1.0))
      dut.io.in1.real.poke(toSFix(1.05)) // exceeds tolerance
      dut.io.in0.imag.poke(toSFix(-0.5))
      dut.io.in1.imag.poke(toSFix(-0.6))
      dut.clock.step()
      dut.io.equal.expect(false.B)
    }
  }

  it should "detect equality correctly with pipeline enabled" in {
    test(new Comparator(width = 16, binaryPoint = 8, tolerance = 2, pipeline = true)) { dut =>
      def toSFix(value: Double): SInt = {
        (value * (1 << dut.binaryPoint)).toInt.S
      }

      dut.io.in0.real.poke(toSFix(2.0))
      dut.io.in0.imag.poke(toSFix(3.0))
      dut.io.in1.real.poke(toSFix(2.0))
      dut.io.in1.imag.poke(toSFix(3.0))
      dut.clock.step()
      dut.io.equal.expect(true.B)

      dut.io.in0.real.poke(toSFix(2.0))
      dut.io.in1.real.poke(toSFix(2.007))
      dut.io.in0.imag.poke(toSFix(3.0))
      dut.io.in1.imag.poke(toSFix(2.993))
      dut.clock.step()
      dut.io.equal.expect(true.B)

      dut.io.in0.real.poke(toSFix(2.0))
      dut.io.in1.real.poke(toSFix(2.05))
      dut.io.in0.imag.poke(toSFix(3.0))
      dut.io.in1.imag.poke(toSFix(2.94))
      dut.clock.step()
      dut.io.equal.expect(false.B)
    }
  }
}
