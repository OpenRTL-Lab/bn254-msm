<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/interface.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 原生接口

[English](interface.en.md)

`Bn254Msm` 使用单时钟、同步高有效复位和四组 ready/valid 通道完成独立 MSM 任务；本文件定义端口、握手、错误响应与输入合法性责任，便于直接集成而无需 SoC 或总线适配。

每个任务计算 Q = Σ_i [s_i]P_i，返回一个点。生成容量为2至65,536之间的2的幂，随包65,536；实际 length 可为1至容量的任意整数。坐标采用普通无符号整数 0 ≤ x,y < p，标量满足 0 ≤ s < r。p 是坐标域模数，r 是不同的 G1 群阶，精确值见 [电路功能与数学模型](mathematical-model.md)；两者端口都不采用 Montgomery 编码。

## 通道与字段

以下为 Chisel 字段名；SV 端口对应 `io_<通道>_<字段>`，payload 对应 `io_<通道>_bits_<字段>`。所有位宽以 bit 为单位。

| 通道 | 方向 | payload | 定义 |
| --- | --- | --- | --- |
| task | 输入 | tag:32，mode:2，length:17 | mode=0；1≤length≤capacity |
| input | 输入 | tag:32，scalar:256，infinity:1，x:256，y:256，last:1 | 恰好length个点标量对；最后一个last=1，其余0 |
| output | 输出 | tag:32，infinity:1，x:256，y:256，last:1 | 完整MSM结果；last恒1 |
| completion | 输出 | tag:32，status:3 | 任务完成或错误状态 |

任务标签须与全部输入记录一致。有限点坐标、标量采用规范整数，具体参数见 [电路功能与数学模型](mathematical-model.md)。无穷点必须使用 `infinity=1,x=0,y=0`。

## 时序与背压

每个通道在时钟上升沿 `valid && ready` 时传输一次；发送方须在阻塞时保持valid和payload。输出与completion在背压下保持不变，验证环境对其稳定性有断言。

本配置只允许一个未完成任务。任务接受后载入并转换输入点，逐窗口计算并输出结果；结果被接受后才发送completion，completion被接受后回到空闲并接收下一任务。不能假设input每周期都ready，必须按握手送入；理想供数意味着发送方在模块可接受时立即提供数据，不改变模块内部时序。

复位 `reset` 同步、高有效，清除控制状态并丢弃在途任务；释放后重新提交完整任务。无需初始化或复位全部SRAM数据，桶有效位在使用前清理。

## 错误行为

| status | 含义 | 行为 |
| --- | --- | --- |
| 0 | 成功 | 一条结果后跟一条completion |
| 1 | 非法mode/length，或scalar≥r | 无结果，仅completion |
| 2 | 输入tag不一致，或last位置不正确 | 无结果，仅completion |
| 3 | x/y≥p，或无穷编码坐标非零 | 无结果，仅completion |

非法控制任务不接受输入。输入出错时继续按声明length排空，不因提前last提前结束；首次错误保持。单条记录同时有多种错误时按帧格式、标量范围、点编码的顺序选择状态。未定义状态值不由本实现产生。

调用方必须提供合法曲线点；硬件的坐标范围检查不等于曲线方程或子群成员检查。本G1余因子为1，合法曲线点属于该G1群。外部接口若接受任意编码，应在送入本模块前完成相应解码与点合法性检查。

## 存储与集成

[files.f](../rtl/v1.0.0/n65536-w8/files.f) 在仓库根目录解析，包含完整产品模块闭包，不含验证环境。产品点表和桶表采用同步单读单写存储契约、1周期读延迟；硬件集成时可按该契约进行目标存储映射。未定义同地址同时读写的行为，不得据此推导额外保证。
