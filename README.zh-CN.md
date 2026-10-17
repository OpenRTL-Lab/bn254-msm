<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: README.zh-CN.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# bn254-msm 硬件加速器

[English](README.md)

本仓库提供使用 **Chisel** 开发的 BN254-G1 多标量乘法（MSM）独立硬件加速器，计算一组椭圆曲线点的标量加权和。它可通过原生接口集成到 ASIC 或 FPGA，用于 BN254 零知识证明中的 Groth16 证明计算、KZG 多项式承诺和其他 G1 点线性组合计算。

采用 [MIT 许可证](LICENSE)，允许免费商用、修改和再分发，须保留版权及许可声明；外部资料与工具见[第三方声明](THIRD_PARTY_NOTICES.md)。

[Release v1.0.0](https://github.com/OpenRTL-Lab/bn254-msm/releases/tag/v1.0.0) · [原生接口](docs/interface.md) · [复现指南](docs/reproduction.md) · [性能测试](docs/performance.md) · [综合结果](docs/synthesis-summary.md)

## 计算与任务单位

一个任务从 n 条点/标量记录计算 Q = sum_i [s_i] P_i，返回一个仿射点或无穷点，而非 n 个独立标量乘结果。随包容量为 65,536 条，运行时长度 n 可为 1 至该容量。下方快速命令中的 --capacity 是硬件上限，--n 8 选择八条记录的验证任务。

固定 8 位 Pippenger 窗口把标量拆成 32 个基数 256 的数字，并非把标量限制为八位。外部坐标和标量是普通无符号 256 位整数：坐标满足 0 <= x,y < p，其中 p 是 BN254 坐标域模数；标量满足 0 <= s < r，其中 r 是不同的 G1 群阶。Jacobian 坐标和 Montgomery 模乘是内部表示，调用方提供规范仿射点，而非 Montgomery 残数。精确 p/r 值和无穷点编码见[电路功能与数学模型](docs/mathematical-model.md)。

每次只有一个任务在途。点表和桶存储使用一读口、一写口（1R1W），同步读延迟为一拍；集成 SRAM 时须保持该存储约定。主机须验证曲线成员资格，而非仅检查坐标范围。

## 配置速览

| 集成问题 | 发布的硬件 |
| --- | --- |
| 计算 | BN254-G1 椭圆曲线多标量乘法（MSM）：Q = sum [s_i] P_i |
| 随包配置 | 容量为 65,536 个点/标量对，固定窗口宽度 8；顶层 `Bn254Msm` |
| 数据通路 | Pippenger 调度、Jacobian 点运算与 Montgomery 域乘法 |
| 原生边界 | 一个任务；带标签的标量/仿射点流；一个仿射结果或无穷点；完成通知 |
| 参考模型前提 | C OpenSSL EC 参考模型与独立仿射 Python 算术；需要 OpenSSL 开发头文件 |

## 快速开始

从 [Release v1.0.0](https://github.com/OpenRTL-Lab/bn254-msm/releases/tag/v1.0.0) 下载源码包，或克隆当前发布快照：

```bash
git clone --branch v1.0.0 --depth 1 https://github.com/OpenRTL-Lab/bn254-msm.git
cd bn254-msm
```

按[工具链说明](docs/toolchain.md)安装准确依赖，然后在仓库根目录使用全新输出目录运行：

```bash
python3 tools/emit_sv.py --out build/emit --capacity 65536 --n 8 --tasks 3
```

该生成命令不是完整 RTL 回归。文档规定的验证入口是：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

预期结果、负控判读以及短功能验证与完整性能运行的区别见[复现指南](docs/reproduction.md)。

## 验证与集成边界

默认回归在完整发布容量下，对八点任务运行十一种模式，覆盖结果检查、停顿、错误 tag/last、标量/坐标越界、输入/计算期间复位和参考值单比特负控。另有 --bundled 命令检查随包产品。65,536 点的三任务性能负载明确属于单独长仿真，不是默认短回归。

坐标和标量必须规范，调用方须验证曲线点：坐标范围检查不能证明曲线方程成立。本模块仅计算 G1 MSM，不提供 G2、配对、完整 Groth16 证明器或 EVM 预编译协议。存储映射须保持文档规定的同步 1R1W 行为。点运算依赖数据，因此特殊短输入的时间不能替代大型随机任务的性能。

### 正确理解性能

对于规定的理想接口 65,536 条记录负载，首任务按含两端边沿的 C - A + 1 计得延迟 805,882,537 周期，A 为任务受理边沿，C 为完成受理边沿。随后两个完成间隔平均为 805,925,780.5 周期，假定 500 MHz 对应 0.620405 任务/s，并非首任务延迟的倒数。首任务换算为 1.611765 秒。点运算引擎运行 793,084,120 / 805,882,537 周期，即任务窗口的 98.4119%；这不是整芯片利用率或外部带宽测量。

这些是理想原生接口条件下记录的 RTL 周期测量。按 MHz 换算的时间/速率不是板级或硅后实测。Design Compiler 结果仅适用于文档规定的配置、库工艺角和约束，不代表布局布线签核或系统吞吐率。条件见[性能测试](docs/performance.md)与[综合结果](docs/synthesis-summary.md)。

## 仓库内容

- **Chisel 设计**：[operators/msm-weierstrass/src/main/scala/zk/msm/](operators/msm-weierstrass/src/main/scala/zk/msm/) 提供 Montgomery 模乘、Jacobian 点运算及 Pippenger 调度；[Env.scala](operators/msm-weierstrass/src/main/scala/zk/msm/Env.scala) 是验证环境，不属于产品电路。
- **生成好的 SV**：[产品 SV 目录](rtl/v1.0.0/n65536-w8/) 提供最大65,536点、8位窗口的独立产品，顶层为 [Bn254Msm](rtl/v1.0.0/n65536-w8/Bn254Msm.sv)，集成文件见 [files.f](rtl/v1.0.0/n65536-w8/files.f)。
- **参考与仿真**：[C 参考模型](operators/msm-weierstrass/cmodel/reference.c) 使用 OpenSSL 通用椭圆曲线运算；[Python 参考核对](tools/check_reference.py) 用独立大整数仿射模型交叉验证；[C++ 时钟驱动](operators/msm-weierstrass/sim/clock.cpp) 驱动 Chisel 验证环境。
- **生成与验证工具**：[SV 生成工具](tools/emit_sv.py) 和 [仿真验证工具](tools/verify.py) 提供完整的电路生成、输入输出比对与周期统计。
- **设计与集成说明**：[硬件架构](docs/architecture.md)、[原生接口](docs/interface.md)、[工具链说明](docs/toolchain.md)、[电路功能与数学模型](docs/mathematical-model.md)、[复现指南](docs/reproduction.md)。
- **性能与实现信息**：[性能测试](docs/performance.md) 说明延迟、吞吐率及计算单元有效忙率；[综合结果](docs/synthesis-summary.md) 转述工艺、约束、面积、setup 与逻辑级数。

## 应用场景

当系统需要计算 BN254-G1 上的 $Q=\sum_i[s_i]P_i$ 时，可以将点和标量流送入本模块，并取得完整仿射结果点；例如以下计算位置：

- **Groth16 证明端**：对证明密钥中的 G1 点与见证相关标量进行线性组合。[Groth16 证明端实现](https://github.com/arkworks-rs/groth16/blob/master/src/prover.rs) 展示了群线性组合的使用位置。
- **采用 BN254 的 KZG 多项式承诺**：计算 $C=\sum_i[a_i][\tau^i]G_1$，也可用于打开证明中商多项式的同类 G1 承诺；多项式处理与可信设置由外部负责。参见 [KZG 多项式承诺](https://docs.rs/ark-poly-commit/latest/ark_poly_commit/kzg10/index.html)。
- **证明验证与批处理中的 G1 线性组合**：例如公共输入与验证密钥的加权和，或协议规定的批量组合；各协议的标量、点集及外围校验由集成方提供。

这些是部分应用示例，并不限制使用范围。曲线参数与以太坊 [EIP-196 曲线定义](https://eips.ethereum.org/EIPS/eip-196) 一致；本模块采用独立原生任务协议，并非 EVM 预编译合约接口。

## 性能指标

65,536 点任务的理想接口延迟为 805,882,537 周期，连续三任务测得约 $1.240809\times10^{-9}$ 任务/周期；假定 500 MHz，首任务延迟为 1.611765 s，随后实测完成间隔对应 0.620405 任务/s 和 40,658.83 点标量对/s。点运算引擎运行周期约占首任务窗口的 98.41%。详见[性能测试](docs/performance.md)。

## 数学模型

电路在固定 BN254 基域上的曲线 $y^2=x^3+3$ 中计算 MSM，接收1至65,536个规范标量与合法 G1 点，输出规范仿射点或无穷点，并返回32位任务标签。 详见[电路功能与数学模型](docs/mathematical-model.md)。

## 快速复现

复现流程包含工具准备、Chisel 生成、独立 C/Python 参考核对、新生成与随包 SV 的功能测试，以及理想接口周期性能统计。 详见[复现指南](docs/reproduction.md)。

## 综合结果

随包配置通过 TSMC 28 nm HPC+ SSG 0.81 V、−40°C、500 MHz及10% setup时钟不确定度约束：setup违例端点为0，99.9607%的端点最差setup路径逻辑级数严格小于50，总映射cell面积约31.174924 mm²（包含844个 SRAM 宏）。 详见[综合结果](docs/synthesis-summary.md)。

## 公开边界

本仓库提供 Chisel、产品 SV、参考模型及完整生成和仿真代码，支持自行复现功能与理想接口周期性能。实际系统的供数、存储延迟与集成性能可由使用者按系统条件评估。

很抱歉，受第三方授权与工程资料保密限制，我们无法提供综合所使用的工艺库、原始 DC Tcl/SDC、报告、映射网表和综合快照；[综合结果](docs/synthesis-summary.md) 提供工具版本、工艺条件、约束及关键结果的转述。

交付范围为 BN254-G1 MSM 单元；SoC、总线适配、完整证明协议、G2运算、配对运算和上板流程由集成方按需组合。输入点必须合法，硬件执行规范范围与帧格式检查；点合法性与其他协议校验的责任详见 [原生接口](docs/interface.md)。

## OpenRTL与定制集成

通过[OpenRTL组织主页](https://github.com/OpenRTL-Lab)、[ZK加速器集合](https://github.com/OpenRTL-Lab/zk-accelerators)及[OpenRTL网站](https://openrtl.top/)查找相关模块与合作联系入口。需要定制数据通路、接口或目标平台适配时可联系维护者；MIT商用权不附加购买服务的要求，不要在公开Issue提交保密规格。

## 发布源码与集成

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
