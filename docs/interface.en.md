<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/interface.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Native interface

[简体中文](interface.md)

`Bn254Msm` uses a single clock, synchronous active-high reset and four ready/valid channels to execute standalone MSM tasks. This document defines ports, handshakes, error responses and input-validity responsibilities for direct integration without a SoC or bus adapter.

One task computes Q = sum_i [s_i]P_i and returns one point. Generated capacity is a power of two from 2 through 65,536, bundled as 65,536; runtime length may be any integer from 1 to capacity. Coordinates are ordinary unsigned integers 0 <= x,y < p; scalars satisfy 0 <= s < r. Here p is the coordinate-field modulus and r the distinct G1 order, defined exactly in [Circuit function and mathematical model](mathematical-model.en.md); neither port uses Montgomery encoding.

## Channels and fields

The table uses Chisel field names. SV ports use `io_<channel>_<field>`; payloads use `io_<channel>_bits_<field>`. Widths are in bits.

| Channel | Direction | Payload | Definition |
| --- | --- | --- | --- |
| task | Input | tag:32, mode:2, length:17 | mode=0; 1≤length≤capacity |
| input | Input | tag:32, scalar:256, infinity:1, x:256, y:256, last:1 | Exactly length pairs; last=1 only on the final record, 0 otherwise |
| output | Output | tag:32, infinity:1, x:256, y:256, last:1 | Complete MSM result; last is always 1 |
| completion | Output | tag:32, status:3 | Completion or error status |

All input tags must equal the task tag. Coordinates and scalars are canonical integers as defined in [Circuit function and mathematical model](mathematical-model.en.md). Infinity must use `infinity=1,x=0,y=0`.

## Timing and backpressure

A transfer occurs on a rising edge with `valid && ready`. The sender holds valid and payload stable while blocked. Output and completion stability under backpressure are asserted by the verification environment.

The configuration supports one outstanding task. It loads/converts points, computes each window and presents the result. Completion is sent only after result acceptance; a new task can be accepted after completion acceptance. Input readiness is not assumed every cycle: obey the handshake. Ideal supply means the sender provides data immediately whenever the module can accept it, without changing internal timing.

`reset` is synchronous and active high. It clears control and discards the in-flight task; resubmit a complete task after release. SRAM contents do not require whole-array initialization or reset; bucket-valid bits are cleared before use.

## Errors

| status | Meaning | Behavior |
| --- | --- | --- |
| 0 | Success | One result followed by one completion |
| 1 | Invalid mode/length, or scalar≥r | Completion only |
| 2 | Input tag mismatch or incorrect last position | Completion only |
| 3 | x/y≥p, or nonzero coordinates for infinity | Completion only |

An invalid control task consumes no input. An input error drains exactly the declared length, not merely until an early last flag; the first error is retained. Within a record, precedence is framing, scalar range, then point encoding. Other status values are not emitted by this implementation.

The caller supplies valid curve points. Coordinate-range checks do not implement the curve equation or subgroup membership test. G1 has cofactor 1, so valid curve points belong to this G1 group. Interfaces that accept arbitrary encodings should decode and validate points before submitting them.

## Memory and integration

[files.f](../rtl/v1.0.0/n65536-w8/files.f) resolves from the repository root and contains the complete product closure, excluding verification. Point and bucket memories use a synchronous 1-read/1-write contract with one-cycle read latency. Target memories can be mapped to that contract. Simultaneous same-address read/write behavior is unspecified; no extra guarantee should be inferred.
