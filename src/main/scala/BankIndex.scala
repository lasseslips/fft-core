import Stage64.Stage64
import chisel3._
import chisel3.util._



object Stage64 extends Enumeration {
  type Stage64 = Value
  val Input, Stage1, Stage2 = Value
}

//Look up table for the Bank index and bank address
object BankIndex {
  def apply(stage: Stage64, idx: UInt): (UInt, UInt) = {
    require(idx.getWidth <= 6, "Index for N=64 must be <= 6 bits wide")

    stage match {
      case Stage64.Input =>
        (BankLUT.inputBankLut64(idx), BankLUT.inputAddrLut64(idx))
      case Stage64.Stage1 =>
        (BankLUT.stage1BankLut64(idx), BankLUT.stage1AddrLut64(idx))
      case Stage64.Stage2 =>
        (BankLUT.stage2BankLut64(idx), BankLUT.stage2AddrLut64(idx))
    }

  }


}



object BankLUT {
  // ===== N64 LUTs =====
  // --- input ---
  def inputBankLut64 = VecInit(Seq(
    0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 1.U(3.W),
    1.U(3.W),  1.U(3.W), 1.U(3.W), 1.U(3.W), 1.U(3.W), 1.U(3.W), 1.U(3.W), 2.U(3.W), 2.U(3.W),
    2.U(3.W),  2.U(3.W), 2.U(3.W), 2.U(3.W), 2.U(3.W), 2.U(3.W), 3.U(3.W), 3.U(3.W), 3.U(3.W),
    3.U(3.W),  3.U(3.W), 3.U(3.W), 3.U(3.W), 3.U(3.W), 4.U(3.W), 4.U(3.W), 4.U(3.W), 4.U(3.W),
    4.U(3.W),  4.U(3.W), 4.U(3.W), 4.U(3.W), 5.U(3.W), 5.U(3.W), 5.U(3.W), 5.U(3.W), 5.U(3.W),
    5.U(3.W),  5.U(3.W), 5.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W),
    6.U(3.W),  6.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W),
    7.U(3.W)
  ))

  def inputAddrLut64 = VecInit(Seq(
    0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W),
    1.U(3.W),  2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W),
    2.U(3.W),  3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W),
    3.U(3.W),  4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W),
    4.U(3.W),  5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W),
    5.U(3.W),  6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W),
    6.U(3.W),  7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W),
    7.U(3.W)
  ))

  // --- stage1 ---
  def stage1BankLut64 = VecInit(Seq(
    0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 1.U(3.W),
    2.U(3.W),  3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 2.U(3.W), 3.U(3.W),
    4.U(3.W),  5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W),
    6.U(3.W),  7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W),
    0.U(3.W),  1.U(3.W), 2.U(3.W), 3.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W),
    2.U(3.W),  3.U(3.W), 4.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W),
    4.U(3.W),  5.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W),
    6.U(3.W)
  ))

  def stage1AddrLut64 = VecInit(Seq(
    0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W),
    1.U(3.W),  2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W),
    2.U(3.W),  3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W),
    3.U(3.W),  4.U(3.W), 5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W),
    4.U(3.W),  5.U(3.W), 6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W),
    5.U(3.W),  6.U(3.W), 7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W),
    6.U(3.W),  7.U(3.W), 0.U(3.W), 1.U(3.W), 2.U(3.W), 3.U(3.W), 4.U(3.W), 5.U(3.W), 6.U(3.W),
    7.U(3.W)
  ))

  // --- stage2 ---
  def stage2BankLut64 = VecInit(Seq(
    0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 0.U(3.W), 1.U(3.W),
    1.U(3.W),  1.U(3.W), 1.U(3.W), 1.U(3.W), 1.U(3.W), 1.U(3.W), 1.U(3.W), 2.U(3.W), 2.U(3.W),
    2.U(3.W),  2.U(3.W), 2.U(3.W), 2.U(3.W), 2.U(3.W), 2.U(3.W), 3.U(3.W), 3.U(3.W), 3.U(3.W),
    3.U(3.W),  3.U(3.W), 3.U(3.W), 3.U(3.W), 3.U(3.W), 4.U(3.W), 4.U(3.W), 4.U(3.W), 4.U(3.W),
    4.U(3.W),  4.U(3.W), 4.U(3.W), 4.U(3.W), 5.U(3.W), 5.U(3.W), 5.U(3.W), 5.U(3.W), 5.U(3.W),
    5.U(3.W),  5.U(3.W), 5.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W), 6.U(3.W),
    6.U(3.W),  6.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W), 7.U(3.W),
    7.U(3.W)
  ))

  def stage2AddrLut64 = VecInit(Seq(
    0.U(3.W), 7.U(3.W), 6.U(3.W), 5.U(3.W), 4.U(3.W), 3.U(3.W), 2.U(3.W), 1.U(3.W), 1.U(3.W),
    0.U(3.W),  7.U(3.W), 6.U(3.W), 5.U(3.W), 4.U(3.W), 3.U(3.W), 2.U(3.W), 2.U(3.W), 1.U(3.W),
    0.U(3.W),  7.U(3.W), 6.U(3.W), 5.U(3.W), 4.U(3.W), 3.U(3.W), 3.U(3.W), 2.U(3.W), 1.U(3.W),
    0.U(3.W),  7.U(3.W), 6.U(3.W), 5.U(3.W), 4.U(3.W), 4.U(3.W), 3.U(3.W), 2.U(3.W), 1.U(3.W),
    0.U(3.W),  7.U(3.W), 6.U(3.W), 5.U(3.W), 5.U(3.W), 4.U(3.W), 3.U(3.W), 2.U(3.W), 1.U(3.W),
    0.U(3.W),  7.U(3.W), 6.U(3.W), 6.U(3.W), 5.U(3.W), 4.U(3.W), 3.U(3.W), 2.U(3.W), 1.U(3.W),
    0.U(3.W),  7.U(3.W), 7.U(3.W), 6.U(3.W), 5.U(3.W), 4.U(3.W), 3.U(3.W), 2.U(3.W), 1.U(3.W),
    0.U(3.W)
  ))

}
