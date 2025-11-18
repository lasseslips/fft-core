import chisel3._
import chisel3.util._
import scala.math._

class FPGATestTop(val fftSize: Int = 8, val width: Int = 16, val binaryPoint: Int = 8, val pipeline: Boolean = true, val testCases: Seq[FFTTestCase] = Seq()) extends Module {
    def isPow2(x: Int): Boolean = (x & (x - 1)) == 0
    require(fftSize >= 2 && isPow2(fftSize), "FFT size must be a power of 2 and >= 2")
    require(testCases.nonEmpty, "At least one test case must be provided")
    require(testCases.forall(_.size == fftSize), s"All test cases must have FFT size $fftSize")

    val io = IO(new Bundle {
        // Test control signals
        val startTest = Input(Bool())
        val testComplete = Output(Bool())
        
        // LED output for comparison result
        val ledPass = Output(Bool())
        val ledFail = Output(Bool())
        
        // Debug outputs
        val fftProcessing = Output(Bool())
        val comparisonDone = Output(Bool())

        val currentTestIndex = Output(UInt(log2Ceil(testCases.size).W))
    })

    // State machine for test control
    val sIdle :: sLoadFFTInput :: sFFTProcess :: sLoadIFFTInput :: sIFFTProcess :: sCompare :: sDone :: Nil = Enum(7)
    val state = RegInit(sIdle)

    // Individual ROMs for each FFT inputs for each test (to enable parallel access)
    val inputROMs = Seq.tabulate(fftSize) { i =>
        val data = Seq.tabulate(testCases.size) { j => // Single input for each ROM
            testCases(j).inputData(i)
        }
        Module(new ComplexROM(testCases.size, width, binaryPoint, data))
    }

    // Individual ROMs for each expected result (to enable parallel access)
    val expectedROMs = Seq.tabulate(fftSize) { i =>
        val data = Seq.tabulate(testCases.size) { j =>
            testCases(j).expectedData(i)
        }
        Module(new ComplexROM(testCases.size, width, binaryPoint, data))
    }

    // Individual memories for each FFT output (to enable parallel access)
    // Memory to store FFT outputs
    val fftMems = Seq.tabulate(fftSize) { i =>
        Module(new ComplexMemory(testCases.size, width, binaryPoint))
    }

    // Memory to store IFFT outputs (final outputs)
    val outMems = Seq.tabulate(fftSize) { i =>
        Module(new ComplexMemory(testCases.size, width, binaryPoint))
    }
    
    // FFT core instance
    val fftCore = Module(new ButterflyN(fftSize, width, binaryPoint, pipeline))

    // IFFT core instance
    val ifftCore = Module(new InverseFFT(fftSize, width, binaryPoint, pipeline))

    // Assign twiddle factors
    val totalTwiddleCount = ButterflyNUtils.calcTwiddleCount(fftSize)
    val twiddlesInt = ButterflyNUtils.generateTwiddleFactors(fftSize)
    val twiddles = ButterflyNUtils.twiddlesToFixedPoint(twiddlesInt, width, binaryPoint)
    for (i <- 0 until totalTwiddleCount) {
        fftCore.io.twiddles(i).real := twiddles(i)._1.S
        fftCore.io.twiddles(i).imag := twiddles(i)._2.S
        ifftCore.io.twiddles(i).real := twiddles(i)._1.S
        ifftCore.io.twiddles(i).imag := twiddles(i)._2.S
    }

    // Two sets of comparators: one to compare FFT outputs against expected FFT results,
    // and one to compare IFFT outputs against the original inputs (round-trip check).
    val comparatorsFFT = Seq.tabulate(fftSize) { i =>
        Module(new Comparator(width, binaryPoint, testCases(0).tolerance, pipeline))
    }
    val comparatorsIFFT = Seq.tabulate(fftSize) { i =>
        Module(new Comparator(width, binaryPoint, testCases(0).tolerance, pipeline))
    }
    
    // Calculate expected latency based on pipeline configuration
    // For pipelined version, each butterfly stage adds 1 cycle delay
    val fftLatency = ButterflyNUtils.getLatency(fftSize, pipeline).U
    val comparisonLatency = ComparatorUtils.getLatency(pipeline).U

    
    // Counter for indexing test cases
    val currentTestIndex = RegInit(0.U(log2Ceil(testCases.size).W))
    // Counter for FFT latency
    val delayCounter = RegInit(0.U(log2Ceil(32).W))

    val fftInRegs = RegInit(VecInit(Seq.fill(fftSize)(0.U.asTypeOf(new ComplexFixedPoint.Complex(width, binaryPoint)))))
    val ifftInRegs = RegInit(VecInit(Seq.fill(fftSize)(0.U.asTypeOf(new ComplexFixedPoint.Complex(width, binaryPoint)))))

    // Comparison logic
    val comparisonPass = RegInit(true.B)
    val allDataCompared = RegInit(false.B)
    
    // ROM and memory connections
    // Each input ROM only has address 0 since it contains single element
    for (i <- 0 until fftSize) {
        inputROMs(i).io.addr := currentTestIndex
        expectedROMs(i).io.addr := currentTestIndex

        // Initialize FFT memory (write disabled by default)
        fftMems(i).io.writeEnable := false.B
        fftMems(i).io.writeAddr := currentTestIndex
        fftMems(i).io.writeData := fftCore.io.out(i)
        fftMems(i).io.readAddr := currentTestIndex

        // Initialize output memory (IFFT outputs)
        outMems(i).io.writeEnable := false.B
        outMems(i).io.writeAddr := currentTestIndex
        outMems(i).io.writeData := ifftCore.io.out(i)
        outMems(i).io.readAddr := currentTestIndex

        // Connect FFT comparators: compare expected FFT results (expectedROMs) with fftMems read data
        comparatorsFFT(i).io.in0 := expectedROMs(i).io.data
        comparatorsFFT(i).io.in1 := fftMems(i).io.readData

        // Connect IFFT comparators: compare original inputs (inputROMs) with outMems read data
        comparatorsIFFT(i).io.in0 := inputROMs(i).io.data
        comparatorsIFFT(i).io.in1 := outMems(i).io.readData
    }
    
    
    // FFT core connections - connect all inputs from individual ROMs (using registers to break combinational path)
    for (i <- 0 until fftSize) {
        fftCore.io.in(i) := fftInRegs(i)
        ifftCore.io.in(i) := ifftInRegs(i)
    }

    // Compare both FFT and IFFT results
    val allSamplesPassFFT = Wire(Vec(fftSize, Bool()))
    val allSamplesPassIFFT = Wire(Vec(fftSize, Bool()))
    for (i <- 0 until fftSize) {
        allSamplesPassFFT(i) := comparatorsFFT(i).io.equal
        allSamplesPassIFFT(i) := comparatorsIFFT(i).io.equal
    }

    val passFFT = allSamplesPassFFT.reduce(_ && _)
    val passIFFT = allSamplesPassIFFT.reduce(_ && _)
    comparisonPass := passFFT && passIFFT

    
    // State machine implementation
    switch(state) {
        is(sIdle) {
            delayCounter := 0.U
            allDataCompared := false.B
            
            when(io.startTest) {
                state := sLoadFFTInput
                currentTestIndex := currentTestIndex + 1.U
                when(currentTestIndex === (testCases.size - 1).U) {
                    currentTestIndex := 0.U // Wrap around to first test case
                }
            }
        }

        is(sLoadFFTInput) {
            state := sFFTProcess
            for (i <- 0 until fftSize) {
                fftInRegs(i) := inputROMs(i).io.data
            }
        }

        // FFT processing: run FFT and when complete, write FFT outputs into fftMems
        is(sFFTProcess) {
            when(delayCounter >= fftLatency) {
                // Store all FFT outputs to their respective memories in parallel
                for (i <- 0 until fftSize) {
                    fftMems(i).io.writeEnable := true.B
                    fftMems(i).io.writeAddr := currentTestIndex
                    fftMems(i).io.writeData := fftCore.io.out(i)
                    fftMems(i).io.readAddr := currentTestIndex
                }
                // Move to IFFT processing stage
                state := sIFFTProcess
                delayCounter := 0.U
            }.otherwise {
                delayCounter := delayCounter + 1.U
            }
        }

        is(sLoadIFFTInput) {
            state := sIFFTProcess
            for (i <- 0 until fftSize) {
                ifftInRegs(i) := fftMems(i).io.readData
            }
        }

        // IFFT processing: read from fftMems (readAddr already set previous state), feed IFFT and write outputs to outMems
        is(sIFFTProcess) {
            // Feed IFFT inputs from fft memory read data (available this cycle)
            for (i <- 0 until fftSize) {
                ifftCore.io.in(i) := fftMems(i).io.readData
            }

            // Wait for IFFT latency, then store outputs
            when(delayCounter >= ButterflyNUtils.getLatency(fftSize, pipeline).U) {
                for (i <- 0 until fftSize) {
                    outMems(i).io.writeEnable := true.B
                    outMems(i).io.writeAddr := currentTestIndex
                    outMems(i).io.writeData := ifftCore.io.out(i)
                    outMems(i).io.readAddr := currentTestIndex
                }
                state := sCompare
                delayCounter := 0.U
            }.otherwise {
                delayCounter := delayCounter + 1.U
            }
        }

        is(sCompare) {
            // Wait for comparator latency plus one cycle for memory read latency
            val effectiveCompareLatency = (comparisonLatency + 1.U)
            when(delayCounter >= effectiveCompareLatency) {
                state := sDone
            }.otherwise {
                delayCounter := delayCounter + 1.U
            }


            allDataCompared := true.B
        }
        
        is(sDone) {
            // Test complete, hold results until start test is deasserted
            when(!io.startTest) {
                state := sIdle
            }
        }
    }
    
    // Output assignments
    io.testComplete := (state === sDone)
    io.ledPass := (state === sDone) && comparisonPass && allDataCompared
    io.ledFail := (state === sDone) && (!comparisonPass || !allDataCompared)
    io.fftProcessing := (state === sFFTProcess)
    io.comparisonDone := allDataCompared
    io.currentTestIndex := currentTestIndex
}
