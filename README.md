## Fast Fourier Transform Generator Library
This repository contains a library for generating RTL for the Fast Fourier Transform (FFT) algorithm using Chisel, a hardware construction language embedded in Scala.

Created as part of the "[Agile Hardware Design](https://github.com/schoeberl/agile-hw)" ([02201](https://kurser.dtu.dk/course/02201)) course at DTU.

### Creators : Group 8
- Andreas Lildballe (s214387, [DreasL02](https://github.com/DreasL02))
- Lasse Slipsager (s224007, [lasseslips](https://github.com/lasseslips))
- Henrique Agostinho Loureiro dos Santos de Oliveira (s252981)

### Features
- Parameterizable Butterfly DIT FFT.
  - Can be configured for different FFT sizes (powers of two).
  - Supports fixed-point number representation with customizable bit widths and fractional bits.
  - Uses a recursive architecture for easy scalability.
  - Supports both forward and inverse FFT operations.
- Modular design for easy integration into larger systems
  - Supports Ready/Valid handshaking for streaming data interfaces.
  - Possibility to supply inputs through a UART interface.
- Comprehensive testbench for functional verification using ChiselTest.

### Repository Structure

### Getting Started


## Motivation

## Algorithm Explanation
The 

This repository currently implements the decimation-in-time (DIT) FFT algorithm. 



## Number Representation

## Design and Implementation

## Testing

## Interfacing 

## Synthesis and Performance

## Conclusion and Future Work

## References