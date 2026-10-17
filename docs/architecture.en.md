<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/architecture.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Hardware architecture

[简体中文](architecture.md)

The design organizes BN254-G1 MSM into a standalone native-interface IP using 8-bit-window Pippenger scheduling, Jacobian point microcode and a shared four-limb Montgomery multiplier. The product uses generic synchronous memories and registers without FPGA primitives.

One task returns Q = sum_i [s_i]P_i from n point/scalar pairs. An 8-bit window is one radix-256 digit of a scalar; 32 windows cover the 256-bit port. A bucket collects points with the same nonzero digit in the current window. A limb is a 64-bit part of the internal 256-bit field word, not a parallel task lane. Coordinates use modulus p and scalars use the distinct group order r; exact values are in [Circuit function and mathematical model](mathematical-model.en.md). Inputs are canonical affine points; Jacobian coordinates and Montgomery residues are internal.

## Data flow

Input handshake → coordinate conversion and point-table write → clear bucket validity → read points and select window digits → update buckets → descending bucket reduction from 255 to 1 → eight doublings and window-sum combination → next window → affine normalization → output and completion.

`MsmConfig(maxPoints)` sets generated capacity to a power of two from 2 through 65,536; runtime task length ranges from 1 to capacity. Window width is fixed at 8. The bundled configuration has capacity 65,536, one point engine and one shared Montgomery engine.

## Units

| Unit | Count | Purpose |
| --- | --- | --- |
| Pippenger controller | 1 | Window, bucket, point-table and task scheduling |
| Point microcode engine | 1 | General Jacobian addition, doubling, input conversion and normalization |
| Montgomery multiplier | 1 | Iterative elimination over 4 64-bit limbs; shared multiplication/squaring |
| Point-engine linear path | 1 | Modular addition or subtraction selected by microcode |
| Point table | 1 | 65,536×769 bits: scalar, infinity flag and two coordinates |
| Bucket table | 1 | 256×768 bits: three Jacobian coordinates, with separate validity bits |

Point addition handles infinity, equal points and opposite points. Doubling and normalization handle infinity branches. Final inversion uses square-and-multiply with fixed exponent $p-2$. Each microcoded operation writes one 256-bit register, handshaking with the shared multiplier.

## Registered stages and task scheduling

Each Montgomery limb executes five registered phases: wide product → accumulation → low factor → modulus product → elimination. Four limbs are followed by final correction, giving 21 active work cycles for a normal multiplication. The phases reuse a state machine rather than accepting a new multiplication every cycle.

Point operations share resources and MSM scheduling keeps one task in flight. This configuration is not a cross-task pipeline; latency and batch completion intervals are measured independently in [Performance tests](performance.en.md).

## Product/verification separation

Counters, assertions and per-window logs reside in the Chisel Verification extraction layer, not the bundled product SV. `Env.scala` input/expected ROMs are simulation-only. Product memories have no `$readmem` or initialization-vector dependency.

Chisel `dontTouch` on product I/O preserves the native boundary and runtime length against verification-environment specialization; it is not a downstream synthesis no-optimization constraint. Generic arithmetic, registers and synchronous memories implement the hardware. The memory-mapping contract is defined in [Native interface](interface.en.md).
