<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/synthesis-summary.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 综合结果

[English](synthesis-summary.en.md)

随包 BN254-G1 MSM 配置在 TSMC 28 nm HPC+ SSG 0.81 V、−40°C 的500 MHz、10% setup时钟不确定度约束下通过setup，99.9607%的端点最差路径逻辑级数严格小于50；本文件转述综合条件与面积、时序及路径统计，不包含原始工程资料。

capacity 统计点/标量记录数，window8 指8位标量数字，不是8条处理通道；1R1W 指每个存储各有一个读口和一个写口。WNS 是最差 setup 裕量，TNS 是负 setup 裕量之和。

## 配置与条件

| 项目 | 值 |
| --- | --- |
| 产品/顶层 | capacity65,536，window8，Bn254Msm |
| 综合工具 | Synopsys Design Compiler NXT X-2025.06-SP3 |
| 工艺 | TSMC 28 nm HPC+ |
| 工艺角/电压/温度 | SSG，0.81 V，−40°C |
| 时钟 | 500 MHz，2 ns周期 |
| setup时钟不确定度 | 0.2 ns，周期的10% |
| 时钟transition | 0.1 ns |
| 输入/输出最大delay | 各1 ns |
| 输入transition | 0.125 ns |
| 复位 | 同步高有效 |
| 存储 | 同步1R1W SRAM映射，共844宏 |

以上是本配置的DC映射结果与约束，频率换算采用同一500 MHz。原生端口时序及目标存储契约见 [原生接口](interface.md)。

## Setup结果

| 指标 | 结果 |
| --- | ---: |
| WNS | +0.000023365 ns |
| TNS | 0 ns |
| 违例端点 | 0 |
| 统计端点 | 84,059 |

## 逻辑级数

对84,059个端点逐一导出其最差max/setup完整数据路径，按数据路径经过的组合单元输出引脚 `Z` 或 `ZN` 计一级；不是数报告行数。计数与独立逐点提取交叉核对一致。该覆盖是每端点一条最差路径，不声称枚举所有可能路径。

| 指标 | 路径数/值 |
| --- | ---: |
| 严格小于50级 | 84,026，99.96074186% |
| 恰好50级 | 21 |
| 51级 | 9 |
| 52级 | 3 |
| 最大级数 | 52 |
| 内部→内部 | 81,732 |
| 内部→输出 | 583 |
| 输入→内部 | 1,744 |

## Cell面积

单位由µm²换算为mm²，属于映射cell面积而非芯片外框面积。Buf/Inv包含在组合面积内，不重复求和。

| 分类 | mm² |
| --- | ---: |
| 总cell面积 | 31.174923729 |
| SRAM宏面积 | 31.068569719 |
| 组合面积（含Buf/Inv） | 0.081187904 |
| 时序单元面积 | 0.025166106 |
| 组合及时序合计 | 0.106354011 |
| Buf/Inv（组合面积子集） | 0.005794348 |

点表层级含映射外围约30.644807054 mm²，桶表约0.441745175 mm²，点引擎约0.073099572 mm²；其中模乘引擎是点引擎的子集，约0.042769454 mm²，不能再次加入总计。

## 资料提供范围

很抱歉，由于第三方工艺授权与工程资料保密要求，无法公开工艺库、原始DC Tcl/SDC、原始报告、宏模型、映射网表及综合快照。我们提供上述自编摘要用于实现参考；设计与仿真全链路可按 [复现指南](reproduction.md) 独立执行。
