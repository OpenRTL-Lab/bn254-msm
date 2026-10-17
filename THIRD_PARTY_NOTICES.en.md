<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: THIRD_PARTY_NOTICES.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Third-party notices

[简体中文](THIRD_PARTY_NOTICES.md)

The authored Chisel, generation tools, reference-call code and verification code are MIT-licensed. This document identifies algorithm parameters, mathematical references and external tools; third-party tools retain their own licenses, and their binaries or process libraries are not redistributed here.

## Parameters and mathematics

- [BN254 parameters](https://docs.rs/ark-bn254/latest/ark_bn254/): used to check the base field, scalar order and curve; arkworks software implementation is not copied into this repository.
- [EIP-196 curve definition](https://eips.ethereum.org/EIPS/eip-196): Ethereum's specification of the same curve; this module does not implement that precompile interface.
- [Jacobian formula reference](https://hyperelliptic.org/EFD/g1p/auto-shortw-jacobian.html): mathematical references for short-Weierstrass Jacobian addition/doubling.

References help readers check mathematical meaning and do not imply delivery of external protocols, tools or additional parameter sets.

## Tools

Mill, Scala, Chisel, firtool, Verilator, GCC/G++ and OpenSSL follow their respective upstream licenses. The C model calls general OpenSSL EC/BN APIs without embedding OpenSSL sources; installed OpenSSL versions are used under their own licenses. Versions and purposes are described in [Toolchain](docs/toolchain.en.md).

Commercial use, modification and redistribution of repository contents require retaining the [MIT License](LICENSE) and applicable notices. Third-party process artifacts are not included; boundaries are explained in [Synthesis results](docs/synthesis-summary.en.md).
