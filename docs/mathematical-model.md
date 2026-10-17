<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/mathematical-model.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 电路功能与数学模型

[English](mathematical-model.en.md)

本电路计算固定 BN254-G1 群中的多标量乘法 $Q=\sum_{i=0}^{n-1}[s_i]P_i$；本文件严格定义基域、标量、群运算、输入输出编码与8位窗口分解，供集成及独立验证使用。

## 固定参数

坐标基域为 $\mathbb F_p$，标量采用 $\mathbb Z_r$ 的规范整数代表元：

$$
p=21888242871839275222246405745257275088696311157297823662689037894645226208583,
$$

$$
r=21888242871839275222246405745257275088548364400416034343698204186575808495617.
$$

曲线及基点定义为

$$
E(\mathbb F_p)=\{(x,y):y^2=x^3+3\}\cup\{\mathcal O\},\qquad G=(1,2),\qquad [r]G=\mathcal O.
$$

本 G1 的余因子为1。参数可核对 [BN254 参数](https://docs.rs/ark-bn254/latest/ark_bn254/)；基域与曲线也见 [EIP-196 曲线定义](https://eips.ethereum.org/EIPS/eip-196)。坐标域的模数 $p$ 与标量阶 $r$ 不得混用。

## 任务与结果

$1\le n\le N$，随包产品 $N=65536$。每个输入满足 $0\le s_i<r$；有限点必须位于上述曲线上，坐标为 $0\le x_i,y_i<p$。无穷点使用 `infinity=1,x=0,y=0`；有限点使用 `infinity=0`。标量及坐标端口均256位，无压缩或字节序转换；连接时传送整数位值。

$$
Q=[s_0]P_0+[s_1]P_1+\cdots+[s_{n-1}]P_{n-1}.
$$

结果有限时输出 `infinity=0` 及唯一规范仿射坐标；结果为 $\mathcal O$ 时输出 `infinity=1,x=0,y=0`。标签不参加运算，输出和完成响应原样返回32位任务标签。硬件检查数值范围、无穷编码和帧格式；曲线点合法性由调用方保证，详见 [原生接口](interface.md)。

## 群运算的严格定义

$\mathcal O+P=P+\mathcal O=P$。对有限点 $P=(x,y),T=(u,v)$，若 $x=u$ 且 $y+v=0$，结果为 $\mathcal O$；其余情况下

$$
\lambda=\begin{cases}(v-y)(u-x)^{-1},&x\ne u,\\3x^2(2y)^{-1},&P=T,\end{cases}
\qquad
P+T=(\lambda^2-x-u,\ \lambda(x-(\lambda^2-x-u))-y).
$$

以上全部坐标运算在 $\mathbb F_p$ 中进行。$[0]P=\mathcal O$，$[s]P$ 为重复群加的标量乘法。实现内部使用 Jacobian 坐标 $(X,Y,Z)$ 表示 $(XZ^{-2},YZ^{-3})$，$Z=0$ 表示无穷；最终仿射化保持同一数学结果。

## Pippenger 分解

固定窗口 $w=8$，基数 $b=256$，32个窗口：

$$
d_{i,j}=\left\lfloor s_i/256^j\right\rfloor\bmod256,\qquad
s_i=\sum_{j=0}^{31}d_{i,j}256^j.
$$

标量小于 $r<2^{254}$，最高窗口只有6个可能有效位。对每个窗口

$$
B_{j,k}=\sum_{i:d_{i,j}=k}P_i\quad(1\le k\le255),\qquad
S_j=\sum_{k=1}^{255}[k]B_{j,k}.
$$

从255降到1，累积 `running += B[j,k]`、`sum += running`，得到 $S_j$；从最高窗口向最低窗口依次令 $T\leftarrow[256]T+S_j$，初始 $T=\mathcal O$，最终 $T=Q$。零数字与无穷输入跳过桶更新。

## 内部域表示

设 $R_0=2^{256}$，内部 $\widehat a=aR_0\bmod p$。模乘返回 $\widehat a\widehat bR_0^{-1}\bmod p$，采用4个64位limb的迭代 Montgomery 消去；转换与最终归一化对外恢复规范整数。该表示仅是内部实现，不改变上述接口数学模型。
