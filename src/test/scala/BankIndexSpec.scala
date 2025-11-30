import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class BankIndexSpec extends AnyFlatSpec with ChiselScalatestTester {
def printMatrix(name: String, mat: Array[Array[Int]]): Unit = {
  println(s"\n===== $name =====")
  for (addr <- 0 until 8) {
    val row = mat(addr).map(x => f"$x%2d").mkString("  ")
    println(s"addr $addr:  $row")
  }
  println()
}
  behavior of "BankIndex"
  val inputMatrix = Array(
    Array(0, 8, 16, 24, 32, 40, 48, 56),
    Array(1, 9, 17, 25, 33, 41, 49, 57),
    Array(2, 10, 18, 26, 34, 42, 50, 58),
    Array(3, 11, 19, 27, 35, 43, 51, 59),
    Array(4, 12, 20, 28, 36, 44, 52, 60),
    Array(5, 13, 21, 29, 37, 45, 53, 61),
    Array(6, 14, 22, 30, 38, 46, 54, 62),
    Array(7, 15, 23, 31, 39, 47, 55, 63)
  )
  val inputRef = inputMatrix.transpose

  it should "match Input mapping (from paper Table I)" in {
    test(new Module {
      val io = IO(new Bundle {
        val idx = Input(UInt(6.W))
        val bankAddr = Output(UInt(3.W))
        val bankIndex = Output(UInt(3.W))
      })
      val (bIdx, bAddr) = BankIndex(Stage64.Input, io.idx)
      io.bankIndex := bIdx
      io.bankAddr := bAddr
    }) { dut =>
      val arr = (0 to 63).toArray
      for (index <- arr) {
        dut.io.idx.poke(index.U)
        dut.clock.step()
        val bi = dut.io.bankIndex.peek().litValue.toInt
        val ba = dut.io.bankAddr.peek().litValue.toInt
        assert(index == inputRef(bi)(ba))
      }
    }
  }

  val stage1 = Array(
    Array(0, 8, 16, 24, 32, 40, 48, 56),
    Array(57, 1, 9, 17, 25, 33, 41, 49),
    Array(50, 58, 2, 10, 18, 26, 34, 42),
    Array(43, 51, 59, 3, 11, 19, 27, 35),
    Array(36, 44, 52, 60, 4, 12, 20, 28),
    Array(29, 37, 45, 53, 61, 5, 13, 21),
    Array(22, 30, 38, 46, 54, 62, 6, 14),
    Array(15, 23, 31, 39, 47, 55, 63, 7)
  )

  val stage1Ref = stage1.transpose

  it should "match Stage1 mapping (from paper Table I)" in {
    test(new Module {
      val io = IO(new Bundle {
        val idx = Input(UInt(6.W))
        val bankAddr = Output(UInt(3.W))
        val bankIndex = Output(UInt(3.W))
      })
      val (bIdx, bAddr) = BankIndex(Stage64.Stage1, io.idx)
      io.bankIndex := bIdx
      io.bankAddr := bAddr
    }) { dut =>
      val arr = (0 to 63).toArray
      for (index <- arr) {
        dut.io.idx.poke(index.U)
        dut.clock.step()
        val bi = dut.io.bankIndex.peek().litValue.toInt
        val ba = dut.io.bankAddr.peek().litValue.toInt
        assert(index == stage1Ref(bi)(ba))
      }
    }
  }


  val stage2 = Array(
    Array(0, 9, 18, 27, 36, 45, 54, 63),
    Array(7, 8, 17, 26, 35, 44, 53, 62),
    Array(6, 15, 16, 25, 34, 43, 52, 61),
    Array(5, 14, 23, 24, 33, 42, 51, 60),
    Array(4, 13, 22, 31, 32, 41, 50, 59),
    Array(3, 12, 21, 30, 39, 40, 49, 58),
    Array(2, 11, 20, 29, 38, 47, 48, 57),
    Array(1, 10, 19, 28, 37, 46, 55, 56)
  )

  val stage2Ref = stage2.transpose

  it should "match Stage2 mapping (from paper Table I)" in {
    test(new Module {
      val io = IO(new Bundle {
        val idx = Input(UInt(6.W))
        val bank  = Output(UInt(3.W))
        val addr  = Output(UInt(3.W))
      })

      // First do Stage1 mapping
      val (b1, a1) = BankIndex(Stage64.Stage1, io.idx)

      // Then apply Stage2 PAPER mapping (reverse engineered)
      val b2 = Wire(UInt(3.W))
      val a2 = Wire(UInt(3.W))

      b2 := (b1 - a1)(2,0)        // (bank - addr) mod 8
      a2 := (b1 - (a1 << 1).asUInt)(2,0) // (bank - 2*addr) mod 8

      io.bank := b2
      io.addr := a2

    }) { dut =>
      for (index <- 0 until 64) {
        dut.io.idx.poke(index.U)
        dut.clock.step()
        val bi = dut.io.bank.peekInt().toInt
        val ba = dut.io.addr.peekInt().toInt
        assert(index == stage2Ref(bi)(ba))
      }
    }
  }
}
