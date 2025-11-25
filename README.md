## Fast Fourier Transform Generator Library
This repository contains a library for generating RTL for the Fast Fourier Transform (FFT) algorithm using Chisel, a hardware construction language embedded in Scala.

Created as part of the "[Agile Hardware Design](https://github.com/schoeberl/agile-hw)" ([02201](https://kurser.dtu.dk/course/02201)) course at DTU.

### Creators : Group 8
- Andreas Lildballe (s214387, [DreasL02](https://github.com/DreasL02))
- Lasse Slipsager (s224007, [lasseslips](https://github.com/lasseslips))
- Henrique Agostinho Loureiro dos Santos de Oliveira (s252981)
Github commits accurately reflect individual contributions.

### Features
- Parameterizable pipelined Butterfly FFT.
  - Supports both Decimation-In-Time (DIT) and Decimation-In-Frequency (DIF) architectures using respectively Cooley-Tukey and Gentleman-Sande butterflies.
  - Can be configured for different FFT sizes (powers of two).
  - Supports fixed-point number representation with customizable bit widths and fractional bits.
  - Uses a recursive architecture for easy scalability.
  - Supports both forward and inverse FFT operations.
- Modular design for easy integration into larger systems
  - Supports Ready/Valid handshaking for streaming data interfaces.
  - Possibility to supply inputs through a UART interface.
- Comprehensive testbench for functional verification using ChiselTest.
- FPGA test platform support for real-world performance evaluation.
- Optional python/tcl building and synthesis scripts for evaluating performance on FPGA targets.

### Repository Structure

### Getting Started


## Motivation

## Algorithm Explanation
The discrete fourier transform is defined by the equation:

$ X(k) = \sum_{n=0}^{N-1} x(n) \cdot e^{-j \frac{2 \pi}{N} k n} \quad \text{for } k = 0, 1, \ldots, N-1 $

where:
- $X(k)$ is the output frequency component at index $k$,
- $x(n)$ is the input time-domain sample at index $n$,
- $N$ is the total number of samples.

This sum can be computed directly, but it has a time complexity of O(N^2), which is inefficient for large N. The Fast Fourier Transform (FFT) algorithm reduces this complexity to O(N log N) by exploiting symmetries and redundancies in the computation.
This is achieved through a divide-and-conquer approach, recursively breaking down the DFT into smaller DFTs.

By splitting the sum into different parts, we can reduce the number of computations needed. One common method is the Cooley-Tukey algorithm, which recursively divides the DFT into smaller DFTs of even and odd indexed samples:

$ X(k) = \sum_{m=0}^{N/2-1} x(2m) \cdot e^{-j \frac{2 \pi}{N} k (2m)} + \sum_{m=0}^{N/2-1} x(2m+1) \cdot e^{-j \frac{2 \pi}{N} k (2m+1)} $

Something about butterflys

### Butterfly architectures
The butterfly components are defined as the basic computing units, operating on two input samples, a twiddle factor, and producing two output samples. Two common architectures are the Cooley-Tukey Butterfly and the Gentleman-Sande Butterfly.

The former operates on the inputs $x_0$ and $x_1$ as: 

$ X_0 = x_0 + W_N^k \cdot x_1 $

$ X_1 = x_0 - W_N^k \cdot x_1 $

while the Gentleman-Sande Butterfly operates as:

$ X_0 = x_0 + x_1 $

$ X_1 = (x_0 - x_1) \cdot W_N^k $

The arithmetic cost of the two approaches is the same, leverging one complex multiplication and two complex additions. However, the data flow and ordering of inputs and outputs differ, which can impact the overall FFT architecture.

## Number Representation


## Design and Implementation

## Testing

## Interfacing 

## Synthesis and Performance

## Conclusion and Future Work

## References

https://dsp-book.narod.ru/FFTBB/0270_PDF_C03.pdf