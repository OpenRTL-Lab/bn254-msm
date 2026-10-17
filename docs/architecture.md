<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/architecture.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 硬件架构

[English](architecture.en.md)

本设计通过8位窗口 Pippenger 调度、Jacobian 点微程序和共享4-limb Montgomery 模乘，将 BN254-G1 MSM 组织为独立原生接口IP；产品使用通用同步存储与寄存器，不依赖 FPGA 原语。

一个任务从 n 条点/标量记录求 Q = Σ_i [s_i]P_i。8位窗口是标量的一个基数256数字，256位端口由32个窗口覆盖；一个桶累积当前窗口中数字相同且非零的点。limb 指内部256位域字的一个64位分段，不是并行任务通道。坐标按模数 p 运算，标量以不同的群阶 r 为界，精确参数见 [电路功能与数学模型](mathematical-model.md)。外部输入是规范仿射点，Jacobian 和 Montgomery 残数仅用于内部。

## 数据流

输入握手 → 坐标转换并写点表 → 清桶有效位 → 读点并选择窗口数字 → 更新桶 → 从255到1归约 → 8次倍点与窗口和合并 → 下一窗口 → 最终仿射化 → 输出与完成。

点表容量通过 `MsmConfig(maxPoints)` 在生成时确定；支持2至65,536之间的2次幂。任务length运行时可为1至容量。窗口宽度固定8，不是可变参数。随包配置为capacity=65,536，具有一个点引擎和一个共享模乘引擎。

## 计算单元

| 单元 | 数量 | 作用 |
| --- | --- | --- |
| Pippenger 控制器 | 1 | 窗口、桶、点表和任务状态调度 |
| 点微程序引擎 | 1 | 通用Jacobian加点、倍点、输入转换、仿射化 |
| Montgomery 模乘引擎 | 1 | 4个64位limb迭代消去；共享域乘和平方 |
| 点引擎线性操作路径 | 1 | 按微指令执行模加或模减 |
| 点表 | 1 | 65,536×769 bit：标量、无穷标志、两坐标 |
| 桶表 | 1 | 256×768 bit：Jacobian三坐标；另有有效位 |

通用点加处理无穷输入、相同点、相反点；倍点和仿射化处理无穷分支。最终逆元采用固定指数 $p-2$ 的平方乘法。点微程序每条运算写一个256位寄存器，与共享模乘通过握手衔接。

## 寄存器阶段与任务调度

模乘的每个limb按“宽乘积 → 累加 → 低位因子 → 模数乘积 → 消去”五个寄存器阶段执行，四个limb后做一次最终修正，正常模乘的有效工作阶段为21 clk。阶段复用同一状态机，不接受每周期一个新模乘。

点引擎复用运算单元，MSM调度也只保留一个在途任务。本配置不是跨任务流水线，延迟与批量完成间隔仍分别测量；详见 [性能测试](performance.md)。

## 产品与验证分离

统计计数器、断言和逐窗口日志位于 Chisel Verification 提取层，不属于随包产品SV。`Env.scala` 的输入ROM与期望结果ROM仅用于仿真；产品存储没有 `$readmem` 或初始向量依赖。

产品I/O的Chisel `dontTouch` 保留原生边界与运行时length，防止验证环境常量特化；它不是要求下游综合禁止优化的约束。硬件使用通用乘加、寄存器和同步存储描述，存储映射契约见 [原生接口](interface.md)。
