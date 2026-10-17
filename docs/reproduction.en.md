<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/reproduction.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Reproduction guide

[简体中文](reproduction.md)

This guide covers tool preparation, Chisel generation, regenerated/bundled SV simulation, independent reference checks and the 65,536-point performance test. The default eight-point regression checks correct results, errors, backpressure and reset; a separate command runs full performance measurements.

## Obtain the release snapshot

[Release v1.0.0](https://github.com/OpenRTL-Lab/bn254-msm/releases/tag/v1.0.0)

```bash
git clone --branch v1.0.0 --depth 1 https://github.com/OpenRTL-Lab/bn254-msm.git
cd bn254-msm
```

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

## 1. Prepare tools

Install the tools and OpenSSL development package described in [Toolchain](toolchain.en.md). Installation is a prerequisite for the following generation/verification commands. Check executables from the repository root:

```bash
java -version
mill --version
verilator --version
python3 --version
cc --version
openssl version
```

The compiler must find `<openssl/ec.h>`, `<openssl/bn.h>` and libcrypto. PATH and the environment variables in [Toolchain](toolchain.en.md) select custom installations without developer-specific directories, caches or Git identity.

For the commands below, `--capacity` is generated point/scalar capacity, `--n` is records per test task, and `--tasks` is verification task count. `--jobs` controls compilation parallelism, not arithmetic engines. Use a fresh relative directory under `build/` for every generation or verification run.

## 2. Generate SV only

This command compiles the independent C reference, generates three eight-point tasks and expected results, runs Python cross-checks, and compiles Chisel to emit the 65,536-capacity product and verification environment:

```bash
python3 tools/emit_sv.py --out build/emit --capacity 65536 --n 8 --tasks 3
```

`build/emit/rtl/product.f` lists the product closure; `simulation.f` includes verification modules and extraction layers. The tool rejects verification-ROM/FPGA-primitive dependencies in the product and compares the typical configuration's circuit with bundled SV. Paths are repository-relative and file lists resolve from the root.

Generated capacity is a power of two from 2 through 65,536. `--n` is the test-task length, not a hardwired product length. `--tasks` ranges from 3 through 16. Window width is fixed at 8. `--out` must be a new relative directory under build; existing results are not overwritten.

## 3. Full short regression on regenerated SV

This command generates the design, lints the product, builds Verilator and runs all eleven modes. Defaults are capacity 65,536, eight points per task and three tasks:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

These 0..10 values select verification scenarios through the tool, not the product task mode. The product accepts only task mode=0 for MSM; an invalid mode is injected only in its error scenario.

| Mode | Coverage |
| --- | --- |
| 0 | Ideal interface, all result checks, latency/throughput/utilization |
| 1 | Output/completion backpressure and stability assertions |
| 2 | Early last flag, exact draining and error status |
| 3 | Deliberately corrupt first expected result; detect exactly 1 mismatch |
| 4 | Reset during input; resubmit |
| 5 | Invalid task mode |
| 6 | Out-of-range scalar=r |
| 7 | Out-of-range x=p |
| 8 | Missing final last flag |
| 9 | Incorrect first-record tag |
| 10 | Reset during computation; resubmit |

The mode 3 simulation intentionally returns 1, and the verification tool requires exactly one mismatch, so overall verification succeeds. This checks rejection of a wrong answer rather than ignoring a failure. Other modes require the correct count of results or error completions.

## 4. Full short regression on bundled SV

This command uses a newly generated environment but replaces the DUT with the five modules in [Product SV directory](../rtl/v1.0.0/n65536-w8/), then runs all eleven modes:

```bash
python3 tools/verify.py --out build/verify-bundled --jobs 8 --bundled
```

The three ideal task latencies are 1,488,387, 59,425 and 166,089 cycles. Task two cancels opposite points and task three repeats equal points; data-dependent branches explain the differences from large random tasks. Regenerated and bundled products should match in results and cycles.

## 5. Full ideal-interface performance test

This command runs three consecutive 65,536-point tasks on the 65,536-capacity product, selecting only ideal mode 0. It requires a long clocked simulation:

```bash
python3 tools/verify.py --out build/performance --capacity 65536 --n 65536 --tasks 3 --jobs 8 --modes 0
```

Each generation checks that the product has not been specialized to the test length. Every output must match the C reference; cycles should agree with [Performance tests](performance.en.md). Add `--bundled` and choose a new output directory to run the same large tasks directly on packaged SV.

## 6. Outputs and diagnosis

On success the tool returns 0 and writes `result.json` with configuration, tool versions, mode results and performance. Stage logs remain in that output directory. `mode0.log` records handshake edges, MSM_STATS and per-window counters. Vectors are `vectors/input.hex` and `expected.hex`; the comparator runs in Chisel while C++ supplies only clock, reset and start.

Missing headers/libcrypto require the development package or corrected CPPFLAGS/LDFLAGS. Generation failures are diagnosed in emit.log with tool versions; simulation failures in the corresponding mode log. After correction use a new output directory, retaining old results for comparison.

Direct integration does not need test ROMs or environments. Lint the product from the repository root with:

```bash
verilator --lint-only --top-module Bn254Msm -f rtl/v1.0.0/n65536-w8/files.f
```

Handshakes and memory contracts are defined in [Native interface](interface.en.md).
