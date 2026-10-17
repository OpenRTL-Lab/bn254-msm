<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/performance.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 性能测试

[English](performance.en.md)

最大65,536点、8位窗口配置的3个连续理想接口任务，分别耗时805,882,537、805,929,400、805,922,161 clk，稳态吞吐率约0.620405任务/s（500 MHz换算）；本文件定义任务延迟、独立测得的吞吐率及计算单元有效忙率，并提供可复现的测试方法。

n 是每任务点/标量对数量，r 是 G1 群阶，G=(1,2) 是基点，精确曲线参数见 [电路功能与数学模型](mathematical-model.md)。8位窗口是标量数字的宽度，不是标量总位宽或通道数。下列500 MHz数值仅为单位换算，不是板级或硅后实测。

## 条件与参考

容量65,536，每任务65,536个点标量对，连续3任务，mode0；输入在模块ready时立即供给，输出及completion无外部背压，不加入外部存储延迟或带宽限制。模块内部同步存储读延迟与运算状态机全部保留。测试时钟的单位是clk，秒数由500 MHz对应的2 ns/clk换算。

C参考用OpenSSL通用点乘及点加，独立于硬件分桶和Montgomery实现；输入由固定确定性序列产生，点为 $[k]G$（$1\le k\le65521$），标量为256位随机数对 $r$ 取模。每任务前8条还覆盖0、1、$r-1$、255、256、$2^{253}$、无穷点与相反点。大任务绝大多数为规范满宽随机标量；数值相关分支可影响周期结果，因此该结果对应这里定义的输入集。

8点回归另外覆盖相反点抵消、同点重复桶和无穷结果，由独立Python仿射大整数模型逐任务核对；大任务全部输出与C参考比对。完整命令见 [复现指南](reproduction.md)。

## 指标口径

Chisel环境从任务接受开始记录时钟边沿编号。$A_j$ 为task握手，$F_j$ 为第一条input握手，$O_j$ 为output握手，$C_j$ 为completion握手。本版本采用包含首尾边沿的周期口径：

$$
L_j=C_j-A_j+1,\qquad L_{\mathrm{data},j}=O_j-F_j+1.
$$

吞吐率不由单任务延迟倒推；用连续完成的间隔测量：

$$
I_j=C_j-C_{j-1},\qquad T_{\mathrm{task}}=\frac{m-1}{C_{m-1}-C_0},\qquad T_{\mathrm{pair}}=nT_{\mathrm{task}}.
$$

这里 $m=3,n=65536$。第一个任务作为起始测量，后续两个完成间隔用于持续吞吐。结果明确是3任务连续测量，不是无限任务外推实测。本配置只有一个在途任务；模乘有寄存器阶段，但没有跨任务流水重叠。

## 周期与换算结果

| 任务 | A | F | O | C | 任务延迟clk | 数据延迟clk |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 0 | 1 | 805882535 | 805882536 | 805882537 | 805882535 |
| 2 | 805882537 | 805882538 | 1611811935 | 1611811936 | 805929400 | 805929398 |
| 3 | 1611811937 | 1611811938 | 2417734096 | 2417734097 | 805922161 | 805922159 |

| 指标 | 值 |
| --- | ---: |
| 后两个完成间隔 | 805,929,400；805,922,161 clk |
| 平均完成间隔 | 805,925,780.5 clk/任务 |
| 吞吐率 | $1.2408090474\times10^{-9}$ 任务/clk |
| 点标量对吞吐率 | $8.1317661732\times10^{-5}$ 对/clk |
| 500 MHz首任务延迟 | 1.611765074 s |
| 500 MHz任务吞吐率 | 0.620404524 任务/s |
| 500 MHz点标量对吞吐率 | 40,658.830866 对/s |

## 有效忙率

令 B 为当前统计行的有效周期或请求计数。计数只在有效运算或读请求阶段增加，不把等待、闲置和输出保持算作计算。以完整任务 $L_j$ 为分母，$U=B/L_j$；空闲或未执行该类工作为 $L_j-B$。`MSM_STATS cycles`计数器与包含首尾的任务口径相差2 clk，表格统一使用 $L_j$，不混用分母。点引擎与模乘存在包含关系，不可将忙率相加。

后表原始列 point_busy、mul_busy、product_busy、linear、point_reads、bucket_reads 依次对应下表六行。

首任务具体统计：

| 单元/事件 | 有效clk | 非该工作clk | 占完整任务比例 |
| --- | ---: | ---: | ---: |
| 点运算引擎run | 793084120 | 12798417 | 98.4119% |
| Montgomery有效阶段 | 706838181 | 99044356 | 87.7098% |
| 通用宽乘积product阶段 | 134635844 | 671246693 | 16.7066% |
| 模加/模减微指令 | 14671001 | 791211536 | 1.8205% |
| 点表读请求 | 2097152 | 803785385 | 0.2602% |
| 桶表读请求 | 2095603 | 803786934 | 0.2600% |

通用宽乘积行只计乘任意操作数的product状态，不包含低因子与固定模数乘积状态；读请求行是端口请求事件，而非SRAM电气开关活动率。性能统计层不进入产品SV。

三个任务的主要原始计数：

| 任务 | point_busy | mul_busy | product_busy | linear | point_reads | bucket_reads |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 793084120 | 706838181 | 134635844 | 14671001 | 2097152 | 2095603 |
| 2 | 793130491 | 706879509 | 134643716 | 14671862 | 2097152 | 2095726 |
| 3 | 793123328 | 706873125 | 134642500 | 14671729 | 2097152 | 2095707 |

## 如何获取自己的结果

[仿真验证工具](../tools/verify.py) 写出 `result.json` 中的task/data延迟、completion间隔、独立吞吐率与stats；`mode0.log`包含握手时刻和逐窗口统计。改变任务长度、输入点和标量后应重新测量，并保留相同口径。系统级供数等限制由使用者自行评估；本测试专注理想接口下的电路性能。
