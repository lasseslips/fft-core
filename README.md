# fft-core
FFT accelerator generator written in chisel using the decimation-in-time apporach (Cooley-Tukey).


# The parameters
- N: Number of points in the FFT (must be a power of 2)
- S: Number of stages (number of points calculated in parallel = N / S)
- (Q, P) = (total number of bits, number of integer bits) for the fixed-point representation of the input and output data
- (Qw, Pw) = (total number of bits, number of integer bits) for the fixed-point representation of the twiddle factors
- (Qacc, Pacc) = (total number of bits, number of integer bits) for the fixed-point representation of the internal accumulators
- pipeline: Boolean flag to indicate whether to use pipelining or not




# What we need to do
### Design / Implementation
#### Stage 1
- Make a fixed point complex adder, subtractor and multiplier (and infrastructure for fixed point numbers / complex numbers)
- Make a simple 2 point butterfly
- Make a 4 point FFT using 2 point butterflies

#### Stage 2
- Make a parameterized N point FFT using log2(N) stages of N/2 butterflies
- Add pipelining

#### Stage 3
- Make infrastructure for reusing S butterflies for large N. Need some control logic and memory to store intermediate results.


### Testing
- Make a golden software model in Scala (working on floating points)
- Floating point -> fixed point conversion functions
- Test random inputs and compare with golden model (keeping in mind the quantization errors of the conversions)

### Performance evaluation
- (around stage 2) Synthesis of various different configurations (N, S, Q, P, Qw, Pw, Qacc, Pacc, pipeline)