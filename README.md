<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: README.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# BN254 G1 Multi-Scalar Multiplication Hardware Accelerator

[简体中文](README.zh-CN.md)

This repository provides a standalone BN254-G1 multi-scalar multiplication (MSM) hardware accelerator developed in **Chisel**, computing a scalar-weighted sum of elliptic-curve points. Its native interface enables integration into ASIC or FPGA designs for Groth16 proof generation, KZG polynomial commitments, and other G1 linear-combination computations in BN254-based zero-knowledge systems.

It is licensed under the [MIT License](LICENSE), permitting free commercial use, modification, and redistribution, provided the copyright and license notices are retained; see [Third-party notices](THIRD_PARTY_NOTICES.en.md) for external references and tools.

[Release v1.0.0](https://github.com/OpenRTL-Lab/bn254-msm/releases/tag/v1.0.0) · [Native interface](docs/interface.en.md) · [Reproduction guide](docs/reproduction.en.md) · [Performance tests](docs/performance.en.md) · [Synthesis results](docs/synthesis-summary.en.md)

## Computation and task units

One task computes Q = sum_i [s_i] P_i from n point/scalar records and returns one affine point or infinity, not n individual scalar-multiplication results. The bundled capacity is 65,536 records, while runtime length n may be any value from 1 through that capacity. In the quick command below, --capacity is the hardware bound and --n 8 selects an eight-record verification task.

The fixed 8-bit Pippenger window splits a scalar into 32 radix-256 digits; it does not limit scalars to eight bits. External coordinates and scalars are ordinary unsigned 256-bit integers: coordinates satisfy 0 <= x,y < p for the BN254 coordinate-field modulus p, and scalars satisfy 0 <= s < r for the distinct G1 group order r. Jacobian coordinates and Montgomery multiplication are internal representations; callers supply canonical affine points, not Montgomery residues. Exact p/r values and infinity encoding are in [Mathematical model](docs/mathematical-model.en.md).

Only one task is outstanding. Point and bucket memories use one read port and one write port (1R1W), with one-cycle synchronous reads; preserve that memory contract when integrating SRAMs. The host must validate curve membership, not merely coordinate ranges.

## At a glance

| Integration question | Released hardware |
| --- | --- |
| Computation | Elliptic-curve multi-scalar multiplication (MSM): Q = sum [s_i] P_i on BN254-G1 |
| Bundled configuration | Capacity 65,536 point/scalar pairs, fixed window width 8; top `Bn254Msm` |
| Datapath | Pippenger scheduling, Jacobian point arithmetic and Montgomery field multiplication |
| Native boundary | One task; tagged scalar/affine-point stream; one affine result or infinity; completion |
| Reference prerequisites | C OpenSSL EC reference plus independent affine Python arithmetic; OpenSSL development headers required |

## Quick start

Download the source archive from [Release v1.0.0](https://github.com/OpenRTL-Lab/bn254-msm/releases/tag/v1.0.0), or clone the current release snapshot:

```bash
git clone --branch v1.0.0 --depth 1 https://github.com/OpenRTL-Lab/bn254-msm.git
cd bn254-msm
```

Install the exact prerequisites in [Toolchain](docs/toolchain.en.md), then run from the repository root with a fresh output directory:

```bash
python3 tools/emit_sv.py --out build/emit --capacity 65536 --n 8 --tasks 3
```

This emission command is not a complete RTL regression. The documented verification entry is:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

Follow [Reproduction guide](docs/reproduction.en.md) for expected results, negative-control interpretation and the distinction between short functional and full performance runs.

## Verification and integration boundary

The default regression runs eleven modes on eight-point tasks at the full released capacity: result checks, stalls, malformed tags/last, scalar/coordinate range errors, reset during input/computation and a one-bit reference negative control. A separate --bundled command checks the supplied product. The three-task 65,536-point performance workload is explicitly a separate long simulation, not the default short regression.

Coordinates and scalars must be canonical, and the caller must validate the curve points: coordinate-range checks do not prove the curve equation. This is G1 MSM only, not G2, pairings, a complete Groth16 prover or the EVM precompile protocol. Memory mapping must preserve the documented synchronous 1R1W behavior. Data-dependent point operations mean short special-input timings are not interchangeable with large random-task performance.

### Reading performance correctly

For the specified ideal-interface 65,536-record workload, the first task has inclusive latency C - A + 1 = 805,882,537 cycles, where A is its task-acceptance edge and C its completion-acceptance edge. The following two completion intervals average 805,925,780.5 cycles, giving 0.620405 tasks/s at an assumed 500 MHz; this is not the reciprocal of the first latency. The first task converts to 1.611765 seconds. Its point engine runs for 793,084,120 / 805,882,537 cycles = 98.4119% of the task window; this is not whole-chip utilization or an external bandwidth measurement.

These are documented RTL cycle measurements under ideal native interfaces. MHz-based time/rate conversions are not board or silicon measurements. The reported Design Compiler results apply only to the documented configuration, library corner and constraints; they do not establish place-and-route signoff or system throughput. See [Performance tests](docs/performance.en.md) and [Synthesis results](docs/synthesis-summary.en.md) for conditions.

## Repository contents

- **Chisel design**: [operators/msm-weierstrass/src/main/scala/zk/msm/](operators/msm-weierstrass/src/main/scala/zk/msm/) implements Montgomery multiplication, Jacobian point operations and Pippenger scheduling; [Env.scala](operators/msm-weierstrass/src/main/scala/zk/msm/Env.scala) is the verification environment, not the product circuit.
- **Generated SV**: [Product SV directory](rtl/v1.0.0/n65536-w8/) contains a standalone product with capacity for 65,536 points and an 8-bit window. The top is [Bn254Msm](rtl/v1.0.0/n65536-w8/Bn254Msm.sv); [files.f](rtl/v1.0.0/n65536-w8/files.f) lists the integration sources.
- **References and simulation**: [C reference model](operators/msm-weierstrass/cmodel/reference.c) uses general OpenSSL elliptic-curve arithmetic; [Python reference checks](tools/check_reference.py) cross-check with independent affine big-integer arithmetic; [C++ clock driver](operators/msm-weierstrass/sim/clock.cpp) drives the Chisel verification environment.
- **Generation and verification**: [SV generation tool](tools/emit_sv.py) and [Simulation verification tool](tools/verify.py) provide circuit generation, input/output comparison and cycle statistics.
- **Design and integration**: [Hardware architecture](docs/architecture.en.md), [Native interface](docs/interface.en.md), [Toolchain](docs/toolchain.en.md), [Circuit function and mathematical model](docs/mathematical-model.en.md), [Reproduction guide](docs/reproduction.en.md).
- **Performance and implementation**: [Performance tests](docs/performance.en.md) defines latency, throughput and effective utilization; [Synthesis results](docs/synthesis-summary.en.md) summarizes process, constraints, area, setup and logic depth.

## Applications

Systems that need $Q=\sum_i[s_i]P_i$ on BN254-G1 can stream points and scalars to this module and receive the complete affine result. Example integration positions include:

- **Groth16 provers**: linear combinations of proving-key G1 points and witness-related scalars. The [Groth16 prover implementation](https://github.com/arkworks-rs/groth16/blob/master/src/prover.rs) shows where group linear combinations are used.
- **BN254-based KZG polynomial commitments**: compute $C=\sum_i[a_i][\tau^i]G_1$, including the corresponding G1 commitments to quotient polynomials for opening proofs. Polynomial processing and the trusted setup are external. See [KZG polynomial commitments](https://docs.rs/ark-poly-commit/latest/ark_poly_commit/kzg10/index.html).
- **G1 combinations in verification and batching**: public-input/verifying-key weighted sums and protocol-defined batch combinations, with scalars, points and surrounding checks supplied by the integrator.

These are examples, not an exhaustive list of uses. The curve parameters match the [EIP-196 curve definition](https://eips.ethereum.org/EIPS/eip-196); this module uses its own native task protocol rather than the EVM precompile interface.

## Performance

An ideal-interface 65,536-point task takes 805,882,537 cycles. Three consecutive tasks yield approximately $1.240809\times10^{-9}$ tasks/cycle; at an assumed 500 MHz the first-task latency is 1.611765 s, while the measured subsequent completion intervals give 0.620405 tasks/s and 40,658.83 point-scalar pairs/s. The point engine is actively running for approximately 98.41% of the first task window. See [Performance tests](docs/performance.en.md).

## Mathematical model

The circuit computes MSM on the fixed BN254 base-field curve $y^2=x^3+3$, accepting 1 to 65,536 canonical scalars and valid G1 points, and returning a canonical affine point or infinity together with the 32-bit task tag. See [Circuit function and mathematical model](docs/mathematical-model.en.md).

## Quick reproduction

The reproducible flow covers tool preparation, Chisel generation, independent C/Python reference checks, functional tests of regenerated and bundled SV, and ideal-interface cycle performance measurements. See [Reproduction guide](docs/reproduction.en.md).

## Synthesis results

The bundled configuration passes setup under TSMC 28 nm HPC+ SSG 0.81 V,−40°C, 500 MHz and 10% setup clock uncertainty: zero violating endpoints, 99.9607% of endpoint-worst setup paths strictly below 50 logic levels, and approximately 31.174924 mm² total mapped cell area including 844 SRAM macros. See [Synthesis results](docs/synthesis-summary.en.md).

## Publication scope

Chisel, product SV, reference models and complete generation/simulation code are provided for reproducing functionality and ideal-interface cycle performance. Users can evaluate system-level supply, memory latency and integration performance under their own conditions.

We apologize that third-party licensing and engineering confidentiality prevent us from supplying the process libraries, original DC Tcl/SDC, reports, mapped netlists and synthesis snapshots. [Synthesis results](docs/synthesis-summary.en.md) provides our summary of tool versions, process conditions, constraints and key results.

The deliverable is a BN254-G1 MSM unit. SoCs, bus adapters, complete proof protocols, G2 operations, pairings and board flows can be composed externally. Input points must be valid; hardware checks canonical ranges and framing. Point-validity and protocol responsibilities are specified in [Native interface](docs/interface.en.md).

## OpenRTL and custom integration

Browse [OpenRTL](https://github.com/OpenRTL-Lab), the [ZK accelerator catalogue](https://github.com/OpenRTL-Lab/zk-accelerators) and [OpenRTL website](https://openrtl.top/) for related modules and the cooperation/contact entry. Custom datapaths, interfaces and target-platform adaptation are optional services; MIT commercial use does not require buying a service. Do not submit confidential specifications in public issues.

## Release sources and integration

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
