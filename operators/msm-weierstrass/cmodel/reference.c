/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: BN254 Multi-Scalar Multiplication Hardware Accelerator
 * Chisel hardware for scalar-weighted sums of BN254-G1 elliptic-curve points.
 * Repository: https://github.com/OpenRTL-Lab/bn254-msm
 * File: operators/msm-weierstrass/cmodel/reference.c
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Poseidon2 contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

/* 独立金标准：OpenSSL通用素域点乘加，不使用硬件分桶或Montgomery实现。 */
#include <openssl/bn.h>
#include <openssl/ec.h>
#include <openssl/crypto.h>
#include <assert.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
static uint64_t rng=0x6b19c84da3927e51ULL;
static uint64_t next(void){rng^=rng<<13;rng^=rng>>7;rng^=rng<<17;return rng;}
static void hex(FILE*f,const BIGNUM*b){unsigned char bytes[32];assert(BN_bn2binpad(b,bytes,32)==32);for(int i=0;i<32;i++)fprintf(f,"%02x",bytes[i]);}
static void random_bn(BIGNUM*b){unsigned char bytes[32];for(int i=0;i<4;i++){uint64_t v=next();for(int j=0;j<8;j++)bytes[i*8+j]=(unsigned char)(v>>(8*j));}assert(BN_bin2bn(bytes,32,b));}
int main(int argc,char**argv){
  assert(argc==4);int n=atoi(argv[1]),jobs=atoi(argv[2]);assert(n>=1&&n<=65536&&jobs>=1&&jobs<=16);
  char file[4096];snprintf(file,sizeof(file),"%s/input.hex",argv[3]);FILE*in=fopen(file,"w");assert(in);
  snprintf(file,sizeof(file),"%s/expected.hex",argv[3]);FILE*out=fopen(file,"w");assert(out);
  BN_CTX*ctx=BN_CTX_new();BIGNUM*p=NULL,*order=NULL;assert(ctx);
  assert(BN_dec2bn(&p,"21888242871839275222246405745257275088696311157297823662689037894645226208583"));
  assert(BN_dec2bn(&order,"21888242871839275222246405745257275088548364400416034343698204186575808495617"));
  BIGNUM*a=BN_new(),*b=BN_new(),*x=BN_new(),*y=BN_new(),*s=BN_new(),*k=BN_new(),*one=BN_new();
  assert(a&&b&&x&&y&&s&&k&&one);BN_zero(a);assert(BN_set_word(b,3)&&BN_one(one));
  EC_GROUP*g=EC_GROUP_new_curve_GFp(p,a,b,ctx);assert(g);
  EC_POINT*G=EC_POINT_new(g),*P=EC_POINT_new(g),*Q=EC_POINT_new(g),*T=EC_POINT_new(g);assert(G&&P&&Q&&T);
  assert(BN_set_word(x,1)&&BN_set_word(y,2));assert(EC_POINT_set_affine_coordinates_GFp(g,G,x,y,ctx));
  assert(EC_GROUP_set_generator(g,G,order,one));assert(EC_GROUP_check(g,ctx));
  assert(EC_POINT_mul(g,T,NULL,G,order,ctx));assert(EC_POINT_is_at_infinity(g,T));
  printf("REFERENCE openssl=%s subgroup_rG=pass n=%d jobs=%d seed=%016llx\n",OpenSSL_version(OPENSSL_VERSION),n,jobs,(unsigned long long)rng);
  for(int job=0;job<jobs;job++){
    assert(EC_POINT_set_to_infinity(g,Q));
    for(int i=0;i<n;i++){
      assert(BN_set_word(k,(unsigned long)(next()%65521+1)));assert(EC_POINT_mul(g,P,k,NULL,NULL,ctx));
      random_bn(s);assert(BN_nnmod(s,s,order,ctx));
      /* 边界不替代大样本：大任务绝大多数仍是满宽随机规范标量。 */
      int inf=(i==4);if(i==0)BN_zero(s);if(i==1)assert(BN_one(s));
      if(i==2){assert(BN_copy(s,order));assert(BN_sub_word(s,1));}
      if(i==3||i==5){assert(EC_POINT_copy(P,G));assert(BN_set_word(s,255));if(i==5)assert(EC_POINT_invert(g,P,ctx));}
      if(i==6){assert(EC_POINT_copy(P,G));assert(BN_set_word(s,256));}
      if(i==7){assert(EC_POINT_copy(P,G));assert(BN_one(s));assert(BN_lshift(s,s,253));}
      /* 小样本第2个任务检查无穷结果，第3个检查重复桶同点倍点。 */
      if(n<=8&&job==1){assert(EC_POINT_copy(P,G));assert(BN_one(s));if(i&1)assert(EC_POINT_invert(g,P,ctx));inf=0;}
      if(n<=8&&job==2){assert(EC_POINT_copy(P,G));assert(BN_set_word(s,255));inf=0;}
      if(inf){assert(EC_POINT_set_to_infinity(g,P));BN_zero(x);BN_zero(y);}
      else{assert(EC_POINT_is_on_curve(g,P,ctx)==1);assert(EC_POINT_get_affine_coordinates_GFp(g,P,x,y,ctx));}
      fprintf(in,"%01x",inf);hex(in,x);hex(in,y);hex(in,s);fprintf(in,"\n");
      assert(EC_POINT_mul(g,T,NULL,P,s,ctx));assert(EC_POINT_add(g,Q,Q,T,ctx));
    }
    int inf=EC_POINT_is_at_infinity(g,Q);if(inf){BN_zero(x);BN_zero(y);}else assert(EC_POINT_get_affine_coordinates_GFp(g,Q,x,y,ctx));
    fprintf(out,"%01x",inf);hex(out,x);hex(out,y);fprintf(out,"\n");
    printf("REFERENCE_TASK job=%d infinity=%d\n",job,inf);fflush(stdout);
  }
  fclose(in);fclose(out);EC_POINT_free(G);EC_POINT_free(P);EC_POINT_free(Q);EC_POINT_free(T);EC_GROUP_free(g);
  BN_free(p);BN_free(order);BN_free(a);BN_free(b);BN_free(x);BN_free(y);BN_free(s);BN_free(k);BN_free(one);BN_CTX_free(ctx);
  return 0;
}
