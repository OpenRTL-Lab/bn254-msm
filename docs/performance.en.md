<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/performance.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Performance tests

[简体中文](performance.md)

Three consecutive ideal-interface tasks on the 65,536-point-capacity, 8-bit-window configuration take 805,882,537, 805,929,400 and 805,922,161 cycles, respectively, yielding approximately 0.620405 tasks/s at 500 MHz. This document defines task latency, independently measured throughput and effective utilization, and explains reproduction.

Here n is the number of point/scalar pairs per task, r is the G1 order, and G=(1,2) is the generator; exact curve parameters are in [Circuit function and mathematical model](mathematical-model.en.md). The 8-bit window is a scalar digit, not scalar width or lane count. All 500 MHz conversions below are calculations, not board or silicon measurements.

## Conditions and references

Capacity 65,536;65,536 point-scalar pairs/task; three consecutive tasks in mode 0. Input is supplied immediately when ready, and output/completion are not externally backpressured. No external bandwidth or memory-latency limits are introduced; internal synchronous-read latency and arithmetic state machines remain intact. Times in seconds use 2 ns/cycle at 500 MHz.

The C model uses general OpenSSL point multiplication/addition, independently of hardware buckets and Montgomery arithmetic. A fixed deterministic sequence generates points $[k]G$, $1\le k\le65521$, and 256-bit pseudorandom scalars reduced modulo $r$. The first eight records of each task additionally cover 0,1,$r-1$,255,256,$2^{253}$, infinity and opposite points. Most large-task scalars use the full canonical width. Data-dependent branches affect cycles, so the measurements correspond to this defined input set.

Eight-point regressions additionally cover opposite-point cancellation, repeated equal-point bucket updates and infinity results, cross-checked task by task with independent affine Python arithmetic. Every large-task output is compared with C. Commands appear in [Reproduction guide](reproduction.en.md).

## Metric definitions

The Chisel environment numbers edges from task acceptance. $A_j$ is task handshake, $F_j$ the first input handshake, $O_j$ output handshake, and $C_j$ completion handshake. This version counts both endpoint edges:

$$
L_j=C_j-A_j+1,\qquad L_{\mathrm{data},j}=O_j-F_j+1.
$$

Throughput is measured from consecutive completions, not inferred from single-task latency:

$$
I_j=C_j-C_{j-1},\qquad T_{\mathrm{task}}=\frac{m-1}{C_{m-1}-C_0},\qquad T_{\mathrm{pair}}=nT_{\mathrm{task}}.
$$

Here $m=3,n=65536$. The first task establishes the starting point; the next two completion intervals measure sustained throughput. This is a three-task measurement, not an infinitely long empirical run. The configuration has one outstanding task. Arithmetic stages are registered, but tasks do not overlap in a pipeline.

## Results

| Task | A | F | O | C | Task latency/cycles | Data latency/cycles |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0 | 1 | 805882535 | 805882536 | 805882537 | 805882535 |
| 2 | 805882537 | 805882538 | 1611811935 | 1611811936 | 805929400 | 805929398 |
| 3 | 1611811937 | 1611811938 | 2417734096 | 2417734097 | 805922161 | 805922159 |

| Metric | Value |
| --- | ---: |
| Subsequent completion intervals | 805,929,400;805,922,161 cycles |
| Mean completion interval | 805,925,780.5 cycles/task |
| Task throughput | $1.2408090474\times10^{-9}$ tasks/cycle |
| Point-scalar throughput | $8.1317661732\times10^{-5}$ pairs/cycle |
| First-task latency at 500 MHz | 1.611765074 s |
| Task throughput at 500 MHz | 0.620404524 tasks/s |
| Pair throughput at 500 MHz | 40,658.830866 pairs/s |

## Effective utilization

Let B be the active-cycle/request count for the selected row. Counters increment only for active operations or read requests, excluding waiting, idle and output-holding states. With full task latency $L_j$ as denominator, $U=B/L_j$ and nonparticipating cycles equal $L_j-B$. The `MSM_STATS cycles` counter differs from the inclusive task metric by two cycles; all percentages here consistently use $L_j$. Point-engine and multiplier activity are nested and must not be added.

Raw columns point_busy, mul_busy, product_busy, linear, point_reads and bucket_reads below correspond, in order, to the six rows of this table.

First-task counters:

| Unit/event | Active cycles | Other cycles | Share of full task |
| --- | ---: | ---: | ---: |
| Point engine run | 793084120 | 12798417 | 98.4119% |
| Montgomery work phases | 706838181 | 99044356 | 87.7098% |
| General wide-product phase | 134635844 | 671246693 | 16.7066% |
| Modular add/sub microinstructions | 14671001 | 791211536 | 1.8205% |
| Point-table read requests | 2097152 | 803785385 | 0.2602% |
| Bucket-table read requests | 2095603 | 803786934 | 0.2600% |

The general wide-product row counts only the arbitrary-operand product state, excluding the low-factor and fixed-modulus product states. Memory rows count request events, not electrical switching activity. Statistics layers are excluded from product SV.

Principal counters for all three tasks:

| Task | point_busy | mul_busy | product_busy | linear | point_reads | bucket_reads |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 793084120 | 706838181 | 134635844 | 14671001 | 2097152 | 2095603 |
| 2 | 793130491 | 706879509 | 134643716 | 14671862 | 2097152 | 2095726 |
| 3 | 793123328 | 706873125 | 134642500 | 14671729 | 2097152 | 2095707 |

## Measuring other workloads

[Simulation verification tool](../tools/verify.py) writes task/data latency, completion intervals, independent throughput and statistics to `result.json`; `mode0.log` contains handshake edges and per-window counters. Remeasure changes to task length, points or scalars using the same definitions. System supply limits can be evaluated externally; this flow measures the circuit under ideal native-interface conditions.
