import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ComplexFixedPointSpec extends AnyFlatSpec with ChiselScalatestTester {

  behavior of "ComplexFixedPoint"

  def complexLit(real: Int, imag: Int, w: Int, bp: Int): ComplexFixedPoint.Complex = {
    val c = Wire(new ComplexFixedPoint.Complex(w, bp))
    c.real := real.S(w.W)
    c.imag := imag.S(w.W)
    c
  }

  "Complex add" should "work correctly" in {
    test(new Module {
      val io = IO(new Bundle {
        val out = Output(new ComplexFixedPoint.Complex(16, 8))
      })

      val a = Wire(new ComplexFixedPoint.Complex(16, 8))
      val b = Wire(new ComplexFixedPoint.Complex(16, 8))
      a.real := 100.S
      a.imag := 50.S
      b.real := -20.S
      b.imag := 10.S

      io.out := ComplexFixedPoint.add(a, b)
    }) { dut =>
      dut.io.out.real.expect((100 - 20).S)
      dut.io.out.imag.expect((50 + 10).S)
    }
  }

  "Complex sub" should "work correctly" in {
    test(new Module {
      val io = IO(new Bundle {
        val out = Output(new ComplexFixedPoint.Complex(16, 8))
      })

      val a = Wire(new ComplexFixedPoint.Complex(16, 8))
      val b = Wire(new ComplexFixedPoint.Complex(16, 8))
      a.real := 40.S
      a.imag := 20.S
      b.real := 10.S
      b.imag := 5.S

      io.out := ComplexFixedPoint.sub(a, b)
    }) { dut =>
      dut.io.out.real.expect((40 - 10).S)
      dut.io.out.imag.expect((20 - 5).S)
    }
  }

  "Complex mul" should "work correctly (fixed point scaled)" in {
    test(new Module {
      val io = IO(new Bundle {
        val out = Output(new ComplexFixedPoint.Complex(32, 8))
      })

      val a = Wire(new ComplexFixedPoint.Complex(32, 8))
      val b = Wire(new ComplexFixedPoint.Complex(32, 8))
      a.real := (1.0 * (1 << 8)).toInt.S // 1.0 in Q8
      a.imag := (0.5 * (1 << 8)).toInt.S // 0.5 in Q8
      b.real := (2.0 * (1 << 8)).toInt.S // 2.0 in Q8
      b.imag := (1.0 * (1 << 8)).toInt.S // 1.0 in Q8

      io.out := ComplexFixedPoint.mul(a, b)
    }) { dut =>
      // Expected (1 + j0.5)*(2 + j1) = (2 - 0.5) + j(1 + 1) = (1.5 + j2)
      val realExp = (1.5 * (1 << 8)).toInt
      val imagExp = (2.0 * (1 << 8)).toInt
      dut.io.out.real.expect(realExp.S)
      dut.io.out.imag.expect(imagExp.S)
    }
  }
}
