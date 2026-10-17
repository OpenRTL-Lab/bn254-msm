<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
Repository: https://github.com/OpenRTL-Lab/bn254-msm
File: docs/mathematical-model.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Circuit function and mathematical model

[简体中文](mathematical-model.md)

The circuit computes multi-scalar multiplication $Q=\sum_{i=0}^{n-1}[s_i]P_i$ in the fixed BN254-G1 group. This document defines the fields, group operations, encodings and 8-bit window decomposition precisely for integration and independent verification.

## Fixed parameters

Coordinates belong to $\mathbb F_p$; scalars are canonical integer representatives in $\mathbb Z_r$:

$$
p=21888242871839275222246405745257275088696311157297823662689037894645226208583,
$$

$$
r=21888242871839275222246405745257275088548364400416034343698204186575808495617.
$$

The curve and generator are

$$
E(\mathbb F_p)=\{(x,y):y^2=x^3+3\}\cup\{\mathcal O\},\qquad G=(1,2),\qquad [r]G=\mathcal O.
$$

This G1 has cofactor 1. See [BN254 parameters](https://docs.rs/ark-bn254/latest/ark_bn254/) and the [EIP-196 curve definition](https://eips.ethereum.org/EIPS/eip-196). Coordinate modulus $p$ and scalar order $r$ are distinct.

## Tasks and results

$1\le n\le N$, with bundled capacity $N=65536$. Each input has $0\le s_i<r$; finite points must lie on the curve with $0\le x_i,y_i<p$. Infinity is encoded as `infinity=1,x=0,y=0`; finite points have `infinity=0`. Scalar and coordinate ports are 256-bit integer values, without point compression or byte-order conversion.

$$
Q=[s_0]P_0+[s_1]P_1+\cdots+[s_{n-1}]P_{n-1}.
$$

A finite result returns `infinity=0` and unique canonical affine coordinates; $\mathcal O$ returns `infinity=1,x=0,y=0`. The 32-bit tag is returned unchanged on both output and completion. Hardware checks ranges, infinity encoding and framing; the caller guarantees curve membership as specified in [Native interface](interface.en.md).

## Group operations

$\mathcal O+P=P+\mathcal O=P$. For finite points $P=(x,y),T=(u,v)$, if $x=u$ and $y+v=0$, the result is $\mathcal O$. Otherwise

$$
\lambda=\begin{cases}(v-y)(u-x)^{-1},&x\ne u,\\3x^2(2y)^{-1},&P=T,\end{cases}
\qquad
P+T=(\lambda^2-x-u,\ \lambda(x-(\lambda^2-x-u))-y).
$$

All coordinate arithmetic is over $\mathbb F_p$. $[0]P=\mathcal O$, and $[s]P$ denotes repeated group addition. Internally, Jacobian $(X,Y,Z)$ represents $(XZ^{-2},YZ^{-3})$; $Z=0$ represents infinity. Affine normalization preserves the same mathematical result.

## Pippenger decomposition

The window is fixed at $w=8$, radix $b=256$, with 32 windows:

$$
d_{i,j}=\left\lfloor s_i/256^j\right\rfloor\bmod256,\qquad
s_i=\sum_{j=0}^{31}d_{i,j}256^j.
$$

Since $s_i<r<2^{254}$, only 6 bits can be effective in the highest window. Define

$$
B_{j,k}=\sum_{i:d_{i,j}=k}P_i\quad(1\le k\le255),\qquad
S_j=\sum_{k=1}^{255}[k]B_{j,k}.
$$

Descending from 255 to 1, `running += B[j,k]` and `sum += running` produce $S_j$. Starting with $T=\mathcal O$, process windows from high to low via $T\leftarrow[256]T+S_j$; the final $T$ equals $Q$. Zero digits and infinity inputs skip bucket updates.

## Internal field representation

Let $R_0=2^{256}$ and $\widehat a=aR_0\bmod p$. Multiplication computes $\widehat a\widehat bR_0^{-1}\bmod p$ using iterative Montgomery elimination over four 64-bit limbs. Conversion and final normalization restore canonical integers externally; the internal representation does not alter the interface model.
