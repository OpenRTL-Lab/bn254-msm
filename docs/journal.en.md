<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/journal.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Current release reference

[简体中文](journal.md)

BN254 Multi-Scalar Multiplication Hardware Accelerator, version 1.0.0. Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.

## Deliverables and use

The repository supplies Chisel sources, generated product SystemVerilog, native-interface documentation, mathematical references and reproduction tools. Product file lists distinguish the circuit from verification environments.

## Verification boundary

The reproduction documents define tested configurations, commands and success criteria. Performance results specify workloads and ideal native-interface timing. Synthesis summaries specify the target configuration, tools, corner and constraints; simulation and synthesis do not establish board or silicon qualification.

## Source and licensing

Original files carry organization, project and license notices. Strict-format data uses adjacent license companions. Third-party materials retain their applicable copyright and terms; the root LICENSE defines the project license.

## Source delivery

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
