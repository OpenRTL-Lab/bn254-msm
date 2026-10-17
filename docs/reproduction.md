<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/reproduction.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 复现指南

[English](reproduction.en.md)

本指南从工具准备到Chisel生成、新生成与随包SV仿真、独立参考核对及65,536点性能测试给出完整操作；默认8点短回归同时覆盖正确结果、错误响应、背压和复位，完整性能测试另有命令。

## 获取版本快照

[Release v1.0.0](https://github.com/OpenRTL-Lab/bn254-msm/releases/tag/v1.0.0)

```bash
git clone --branch v1.0.0 --depth 1 https://github.com/OpenRTL-Lab/bn254-msm.git
cd bn254-msm
```

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

## 1. 准备工具

先按 [工具链说明](toolchain.md) 安装工具及OpenSSL开发包。安装是下面生成与验证命令能够执行的前提；在仓库根目录检查可执行工具：

```bash
java -version
mill --version
verilator --version
python3 --version
cc --version
openssl version
```

编译器需能找到 `<openssl/ec.h>`、`<openssl/bn.h>` 和libcrypto。自行安装的工具可通过PATH及 [工具链说明](toolchain.md) 列出的环境变量选择，无需使用项目开发者的目录、缓存或Git身份。

以下命令中 `--capacity` 是生成的点/标量容量，`--n` 是测试每任务记录数，`--tasks` 是验证任务数；`--jobs` 只控制编译并行度，不改变算术引擎数。每次生成或验证均使用 `build/` 下新的相对目录。

## 2. 只生成SV

下面命令编译独立C参考、产生3个8点任务及期望结果、执行Python交叉核对，然后编译Chisel并生成容量65,536的产品与验证环境：

```bash
python3 tools/emit_sv.py --out build/emit --capacity 65536 --n 8 --tasks 3
```

输出 `build/emit/rtl/product.f` 是产品闭包；`simulation.f`包含验证模块与提取层。生成工具验证产品没有验证ROM/FPGA原语，并比较典型配置与随包产品的电路内容。路径为仓库内相对路径，产品文件清单从仓库根目录使用。

容量在生成时可选2至65,536之间的2次幂；`--n` 仅是本次测试任务长度，不是产品硬连线长度；`--tasks` 为3至16。窗口固定8位。`--out` 必须是build下尚未存在的相对目录；不覆盖旧结果。

## 3. 新生成电路完整短回归

下面命令执行生成流程、产品lint、Verilator编译和全部11种验证模式；默认容量65,536、每任务8点、3任务：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

下表0..10是验证工具的场景编号，不是产品 task.mode 编码。产品仅接受 mode=0 执行 MSM；非法任务模式只在对应错误场景中注入。

| 模式 | 验证内容 |
| --- | --- |
| 0 | 理想接口、全部结果核对、延迟/吞吐/忙率 |
| 1 | 输出及完成背压、数据稳定性断言 |
| 2 | 提前last，精确排空与错误状态 |
| 3 | 故意修改第一个期望结果，检测器必须发现1次不匹配 |
| 4 | 输入阶段复位，重新提交 |
| 5 | 非法任务mode |
| 6 | scalar=r范围错误 |
| 7 | x=p坐标范围错误 |
| 8 | 最后一条缺少last |
| 9 | 首记录标签错误 |
| 10 | 计算阶段复位，重新提交 |

模式3的仿真程序预期退出码为1，验证工具要求恰好1次不匹配，因此验证工具整体仍返回成功；这不是跳过失败，而是检查比较器能拒绝错误答案。其他模式要求正确数量的输出或错误completion。

## 4. 随包电路完整短回归

下面命令使用新生成的验证环境，但将DUT替换为 [产品 SV 目录](../rtl/v1.0.0/n65536-w8/) 中的5个模块，再跑全部11种模式：

```bash
python3 tools/verify.py --out build/verify-bundled --jobs 8 --bundled
```

短回归中3个任务的理想任务延迟为1,488,387、59,425、166,089 clk；第二任务为相反点抵消、第三任务为重复同点，因分支不同无需与大随机任务延迟相同。新生成与随包产品均应得到相同结果和周期。

## 5. 完整理想性能测试

下面命令对容量65,536的产品连续执行3个65,536点任务，只选择理想模式0；需要长时间时钟仿真：

```bash
python3 tools/verify.py --out build/performance --capacity 65536 --n 65536 --tasks 3 --jobs 8 --modes 0
```

每次生成都核对产品不被测试任务长度特化。全部输出须逐任务匹配C参考；任务周期应与 [性能测试](performance.md) 一致。若需同样的大任务直接跑随包产品，可添加 `--bundled` 并使用新的输出目录。

## 6. 输出与定位

成功时工具返回0并写 `result.json`，包含configuration、工具版本、模式结果和performance；每阶段日志保存在本次目录。`mode0.log` 给出任务握手时刻、MSM_STATS及逐窗口统计。测试向量位于 `vectors/input.hex` 和 `expected.hex`，仿真环境的比较逻辑在Chisel内，C++只产生时钟、复位和启动。

若缺头文件或libcrypto，先修正开发包或CPPFLAGS/LDFLAGS；若生成失败，检查emit.log与工具版本；若仿真失败，检查对应模式日志。修正后选新的输出目录，旧结果保留便于比较。

直接集成无需测试ROM或环境；仓库根目录的产品lint命令为：

```bash
verilator --lint-only --top-module Bn254Msm -f rtl/v1.0.0/n65536-w8/files.f
```

握手和存储契约见 [原生接口](interface.md)。
