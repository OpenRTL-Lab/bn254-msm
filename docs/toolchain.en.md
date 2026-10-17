<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/toolchain.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Toolchain

[简体中文](toolchain.md)

The repository generates circuits with pinned Mill, Scala, Chisel and firtool versions and verifies them with Verilator, C/C++ and OpenSSL. The table lists verified versions and portable installation requirements; run all entry points from the repository root.

| Tool | Verified version | Purpose |
| --- | --- | --- |
| JDK | OpenJDK 21.0.11 | Mill/Scala/Chisel |
| Mill | 1.1.7 | Dependencies, compilation and generation |
| Scala | 2.13.18 | Chisel sources |
| Chisel | 7.13.0 | Circuit construction and verification extraction |
| firtool | 1.149.0 | SystemVerilog lowering |
| Verilator | 5.050 | SV lint and clocked simulation |
| Python | 3.12 | Scripts and big-integer reference |
| GCC/G++ | 16.2.0 | C reference and generated C++ simulation |
| OpenSSL | 1.1.1k | Independent general elliptic-curve C reference |

`build.mill` pins Scala/Chisel and declares the Mill version; the JDK is supplied by the user. Install matching Mill on PATH or set `MILL` to its invocation command. Chisel resolves matching firtool; an installed copy can be selected with `CHISEL_FIRTOOL_PATH` pointing to the directory containing firtool. The first run downloads dependencies.

A Linux host needs C/C++ compilers, make, Verilator, OpenSSL development headers and libcrypto. Python uses only its standard library. OpenSSL must provide the general EC/BN APIs used by the reference. Scripts link with `-lcrypto` rather than a fixed library path and accept `CC`, `CPPFLAGS`, `CFLAGS` and `LDFLAGS` for custom installations. Compilation handles deprecated EC-API warnings under OpenSSL 3.x separately; recorded measurements use the table versions.

The product requires ordinary SV arithmetic, registers and synchronous memories. C/OpenSSL and verification ROMs are reference-test dependencies, not product modules. See [Reproduction guide](reproduction.en.md) for verification and integration.
