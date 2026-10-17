<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/synthesis-summary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Synthesis results

[简体中文](synthesis-summary.md)

The bundled BN254-G1 MSM configuration passes setup at 500 MHz with 10% setup clock uncertainty in TSMC 28 nm HPC+ SSG 0.81 V,−40°C;99.9607% of endpoint-worst paths have strictly fewer than 50 logic levels. This document summarizes conditions, area, timing and path statistics without original engineering artifacts.

Capacity counts point/scalar records, while window 8 means an 8-bit scalar digit, not eight processing lanes. 1R1W denotes one read port and one write port per memory. WNS is minimum setup slack; TNS sums negative setup slacks.

## Configuration and conditions

| Item | Value |
| --- | --- |
| Product/top | Capacity 65,536, window 8, Bn254Msm |
| Tool | Synopsys Design Compiler NXT X-2025.06-SP3 |
| Process | TSMC 28 nm HPC+ |
| Corner/voltage/temperature | SSG, 0.81 V,−40°C |
| Clock | 500 MHz, 2 ns period |
| Setup clock uncertainty | 0.2 ns, 10% of period |
| Clock transition | 0.1 ns |
| Maximum input/output delay | 1 ns each |
| Input transition | 0.125 ns |
| Reset | Synchronous active high |
| Memory | Synchronous1R1W SRAM mapping, 844 macros |

These are this configuration's DC mapping conditions/results; performance conversion uses the same 500 MHz. Native-port timing and memory contracts appear in [Native interface](interface.en.md).

## Setup

| Metric | Result |
| --- | ---: |
| WNS | +0.000023365 ns |
| TNS | 0 ns |
| Violating endpoints | 0 |
| Counted endpoints | 84,059 |

## Logic depth

The complete worst max/setup data path is exported for each of 84,059 endpoints. Each combinational output pin `Z` or `ZN` encountered on the data path counts as one level; report lines are not levels. Counts agree with an independent point-by-point extraction. Coverage is one worst path per endpoint, not enumeration of every possible path.

| Metric | Paths/value |
| --- | ---: |
| Strictly fewer than 50 levels | 84,026;99.96074186% |
| Exactly 50 | 21 |
| 51 | 9 |
| 52 | 3 |
| Maximum | 52 |
| Internal→internal | 81,732 |
| Internal→output | 583 |
| Input→internal | 1,744 |

## Cell area

Areas are converted fromµm² to mm² and describe mapped cells, not die outline. Buffer/inverter area is included in combinational area and is not added twice.

| Category | mm² |
| --- | ---: |
| Total cells | 31.174923729 |
| SRAM macros | 31.068569719 |
| Combinational, including buffers/inverters | 0.081187904 |
| Sequential | 0.025166106 |
| Combinational plus sequential | 0.106354011 |
| Buffers/inverters, a combinational subset | 0.005794348 |

The point-table hierarchy including mapping peripherals occupies approximately 30.644807054 mm², bucket table 0.441745175 mm² and point engine 0.073099572 mm². The multiplier is nested in the point engine at approximately 0.042769454 mm² and must not be added again.

## Available information

We apologize that third-party process licensing and engineering confidentiality prevent publication of libraries, original DC Tcl/SDC, reports, macro models, mapped netlists and synthesis snapshots. This authored summary supplies implementation reference; the complete design/simulation flow is independently executable using [Reproduction guide](reproduction.en.md).
