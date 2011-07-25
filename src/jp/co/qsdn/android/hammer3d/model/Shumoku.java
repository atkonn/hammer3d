/*
 * Copyright (C) 2011 QSDN,Inc.
 * Copyright (C) 2011 Atsushi Konno
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.co.qsdn.android.hammer3d.model;

import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.opengl.GLUtils;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import java.util.Random;

import javax.microedition.khronos.opengles.GL10;

import jp.co.qsdn.android.hammer3d.Aquarium;
import jp.co.qsdn.android.hammer3d.Bait;
import jp.co.qsdn.android.hammer3d.BaitManager;
import jp.co.qsdn.android.hammer3d.GLRenderer;
import jp.co.qsdn.android.hammer3d.util.CoordUtil;

public class Shumoku implements Model {
  private static final boolean traceBOIDS = false;
  private static final boolean debug = false;
  private static final String TAG = Shumoku.class.getName();
  private static final long BASE_TICK = 17852783L;
  private static boolean mTextureLoaded = false;
  private final FloatBuffer mVertexBuffer;
  private final FloatBuffer mTextureBuffer;  
  private final FloatBuffer mNormalBuffer;  
  private long prevTime = 0;
  private long tick = 0;
  private static final float scale = 0.119717280459159f;
  private float center_xyz[] = {0.944553637254902f, -0.0858584215686275f, 0.00370374509803921f};
  private CoordUtil coordUtil = new CoordUtil();
  private long seed = 0;
  private BaitManager baitManager;
  private boolean enableBoids = true;
  public float[] distances = new float[GLRenderer.MAX_IWASHI_COUNT + 1];
  private Random rand = null;
  public static final float GL_SHUMOKU_SCALE = 3f;
  private float size = 10f * scale * GL_SHUMOKU_SCALE;
  private int shumokuCount;
  /*
   * 仲間、同種
   */
  public static final double separate_dist  = 5.0d * scale * (double)GL_SHUMOKU_SCALE;
  private static double[] separate_dist_xyz = { 
                                    5.404d * scale * (double)GL_SHUMOKU_SCALE, 
                                    0.734d * scale * (double)GL_SHUMOKU_SCALE, 
                                    0.347d * scale * (double)GL_SHUMOKU_SCALE,
                                  };
  public static double[] aabb_org = {
    -separate_dist_xyz[0], -separate_dist_xyz[1], -separate_dist_xyz[2],
    separate_dist_xyz[0], separate_dist_xyz[1], separate_dist_xyz[2],
  };
  public static double[] sep_aabb = {
    0d,0d,0d,
    0d,0d,0d,
  };
  public static double[] al1_aabb = {
    0d,0d,0d,
    0d,0d,0d,
  };
  public static double[] al2_aabb = {
    0d,0d,0d,
    0d,0d,0d,
  };
  public static double[] sch_aabb = {
    0d,0d,0d,
    0d,0d,0d,
  };
  public static double[] coh_aabb = {
    0d,0d,0d,
    0d,0d,0d,
  };
  public static final double alignment_dist1= 15.0d * scale * (double)Iwashi.GL_IWASHI_SCALE;
  public static final double alignment_dist2= 35.0d * scale * (double)Iwashi.GL_IWASHI_SCALE;
  public static final double school_dist    = 70.0d * scale * (double)Iwashi.GL_IWASHI_SCALE;
  public static final double cohesion_dist  = 110.0d * scale * (double)Iwashi.GL_IWASHI_SCALE;
  private float[] schoolCenter = {0f,0f,0f};
  private float[] schoolDir = {0f,0f,0f};

  private enum STATUS {
    TO_CENTER, /* 画面の真ん中へ向かい中 */
    TO_BAIT,   /* 餌へ向かっている最中   */
    SEPARATE,  /* 近づき過ぎたので離れる */
    ALIGNMENT, /* 整列中 */
    COHESION,  /* 近づく */
    TO_SCHOOL_CENTER,   /* 群れの真ん中へ */
    NORMAL,    /* ランダム */
  };

  /** 現在の行動中の行動 */
  private STATUS status = STATUS.NORMAL;

  private enum TURN_DIRECTION {
    TURN_RIGHT, /* 右に曲がり中 */
    STRAIGHT,   /* まっすぐ */
    TURN_LEFT,  /* 左に曲がり中 */
  };

  /** 現在曲がろうとしているかどうか */
  private TURN_DIRECTION turnDirection = TURN_DIRECTION.STRAIGHT;


  private int[] mScratch128i = new int[128];
  private float[] mScratch4f = new float[4];
  private float[] mScratch4f_1 = new float[4];
  private float[] mScratch4f_2 = new float[4];
  private Shumoku[] mScratch4Shumoku = new Shumoku[4];


  /*=========================================================================*/
  /* 現在位置                                                                */
  /*=========================================================================*/
  private float[] position = { 0.0f, 1.0f, 0.0f };
  /*=========================================================================*/
  /* 向き                                                                    */
  /*=========================================================================*/
  private float[] direction = { -1.0f, 0.0f, 0.0f};

  /* 上下 */
  private float x_angle = 0;
  /* 左右 */
  private float y_angle = 0;

  /* angle for animation */
  private float angleForAnimation = 0f;
  /*=========================================================================*/
  /* スピード                                                                */
  /*=========================================================================*/
  public static final float DEFAULT_SPEED = 0.03456f;
  private float speed = DEFAULT_SPEED * 0.5f;
  private float speed_unit = DEFAULT_SPEED / 5f * 0.5f;
  private float speed_max = DEFAULT_SPEED * 3f * 0.5f;
  private float speed_min = speed_unit;
  private float cohesion_speed = speed * 5f * 0.5f;
  private float sv_speed = speed;

  private int shumokuNo = 0;

  public Shumoku(int ii) {

    ByteBuffer nbb = ByteBuffer.allocateDirect(ShumokuData.normals.length * 4);
    nbb.order(ByteOrder.nativeOrder());
    mNormalBuffer = nbb.asFloatBuffer();
    mNormalBuffer.put(ShumokuData.normals);
    mNormalBuffer.position(0);

    ByteBuffer tbb = ByteBuffer.allocateDirect(ShumokuData.texCoords.length * 4);
    tbb.order(ByteOrder.nativeOrder());
    mTextureBuffer = tbb.asFloatBuffer();
    mTextureBuffer.put(ShumokuData.texCoords);
    mTextureBuffer.position(0);

    ByteBuffer vbb = ByteBuffer.allocateDirect(ShumokuData.vertices.length * 4);
    vbb.order(ByteOrder.nativeOrder());
    mVertexBuffer = vbb.asFloatBuffer();

    // 初期配置
    this.rand = new java.util.Random(System.nanoTime() + (ii * 500));
    this.seed = (long)(this.rand.nextFloat() * 5000f);
    position[0] = this.rand.nextFloat() * 8f - 4f;
    position[1] = 0f;
    position[2] = this.rand.nextFloat() * 4f - 2f;

    // 初期方向セット
    x_angle = 0f;
    y_angle = rand.nextFloat() * 360f;
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    // 鰯番号セット
    shumokuNo = ii;
  }

  protected static int[] textureIds = null;
  public static void loadTexture(GL10 gl10, Context context, int resource) {
    textureIds = new int[1];
    Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), resource);
    gl10.glGenTextures(1, textureIds, 0);
    gl10.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);
    GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bmp, 0);
    gl10.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
    gl10.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
    bmp.recycle();
    bmp = null;
    mTextureLoaded = true;
  }
  public static void deleteTexture(GL10 gl10) {
    if (textureIds != null) {
      gl10.glDeleteTextures(1, textureIds, 0);
    }
  }
  public static boolean isTextureLoaded() {
    return mTextureLoaded;
  }

  private float getMoveWidth(float x) {
    /*=======================================================================*/
    /* z = 1/3 * x^2 の2次関数から算出                                       */
    /*=======================================================================*/
    float xt = x / scale + center_xyz[0];
    return xt * xt / 20.0f;
  }


  private void animate() {
    long current = System.currentTimeMillis() + this.seed;
    float nf = (float)((current / 100) % 10000);
    float s = (float)Math.sin((double)nf / 6f);
    if (getTurnDirection() == TURN_DIRECTION.TURN_LEFT) {
      s += -0.2f;
    }
    else if (getTurnDirection() == TURN_DIRECTION.TURN_RIGHT) {
      s += 0.2f;
    }
    s *= scale;
    angleForAnimation = 3.0625f * (float)Math.cos((double)nf / 6f) * -1f;

    /* **DONT EDIT FOLLOWING LINE** */
    /* Generate by perl script */
    //498 166 {-2.254128, -0.000000, -0.427256}
    //504 168 {-2.254128, -0.000000, -0.427256}
    //1338 446 {-2.254128, -0.000000, -0.427256}
    //1347 449 {-2.254128, -0.000000, -0.427256}
    //1356 452 {-2.254128, -0.000000, -0.427256}
    //1362 454 {-2.254128, -0.000000, -0.427256}
    synchronized (mScratch128i) {
      mScratch128i[0] = 166;
      mScratch128i[1] = 168;
      mScratch128i[2] = 446;
      mScratch128i[3] = 449;
      mScratch128i[4] = 452;
      mScratch128i[5] = 454;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //018 006 {-2.149617, -0.200671, -0.533464}
    //021 007 {-2.188389, -0.595234, -0.503895}
    //030 010 {-2.149617, 0.200670, -0.533464}
    //033 011 {-2.188389, 0.588324, -0.503895}
    //036 012 {-2.149617, -0.200671, -0.533464}
    //042 014 {-2.188389, -0.595234, -0.503895}
    //051 017 {-2.149617, 0.200670, -0.533464}
    //057 019 {-2.149617, 0.200670, -0.533464}
    //063 021 {-2.188389, -0.595234, -0.503895}
    //066 022 {-2.149617, -0.200671, -0.533464}
    //072 024 {-2.149617, 0.200670, -0.533464}
    //081 027 {-2.149617, -0.200671, -0.533464}
    //090 030 {-2.149617, -0.200671, -0.533464}
    //147 049 {-2.149617, 0.200670, -0.533464}
    //168 056 {-2.149617, -0.200671, -0.533464}
    //177 059 {-2.149617, -0.200671, -0.533464}
    //420 140 {-2.149617, -0.000000, -0.594055}
    //429 143 {-2.149617, -0.000000, -0.594055}
    //435 145 {-2.149617, -0.200671, -0.533464}
    //438 146 {-2.149617, -0.000000, -0.594055}
    //462 154 {-2.149617, -0.000000, -0.594055}
    //465 155 {-2.149617, 0.200670, -0.533464}
    //474 158 {-2.188389, -0.595234, -0.503895}
    //483 161 {-2.188389, 0.588324, -0.503895}
    //486 162 {-2.149617, -0.000000, -0.594055}
    //489 163 {-2.149617, -0.200671, -0.533464}
    //495 165 {-2.188389, -0.595234, -0.503895}
    //507 169 {-2.188389, 0.588324, -0.503895}
    //513 171 {-2.149617, 0.200670, -0.533464}
    //516 172 {-2.149617, -0.000000, -0.594055}
    //738 246 {-2.188389, 0.588324, -0.503895}
    //741 247 {-2.149617, 0.200670, -0.533464}
    //747 249 {-2.149617, 0.200670, -0.533464}
    //750 250 {-2.188389, 0.588324, -0.503895}
    //756 252 {-2.149617, 0.200670, -0.533464}
    //768 256 {-2.188389, 0.588324, -0.503895}
    //774 258 {-2.188389, -0.595234, -0.503895}
    //783 261 {-2.149617, -0.200671, -0.533464}
    //1173 391 {-2.149617, -0.200671, -0.533464}
    //1191 397 {-2.149617, -0.200671, -0.533464}
    //1206 402 {-2.149617, 0.200670, -0.533464}
    //1224 408 {-2.149617, -0.200671, -0.533464}
    //1230 410 {-2.188389, -0.595234, -0.503895}
    //1233 411 {-2.149617, -0.200671, -0.533464}
    //1236 412 {-2.188389, -0.595234, -0.503895}
    //1245 415 {-2.149617, 0.200670, -0.533464}
    //1248 416 {-2.188389, 0.588324, -0.503895}
    //1251 417 {-2.149617, 0.200670, -0.533464}
    //1257 419 {-2.188389, 0.588324, -0.503895}
    //1344 448 {-2.188389, 0.588324, -0.503895}
    //1365 455 {-2.188389, -0.595234, -0.503895}
    synchronized (mScratch128i) {
      mScratch128i[0] = 6;
      mScratch128i[1] = 7;
      mScratch128i[2] = 10;
      mScratch128i[3] = 11;
      mScratch128i[4] = 12;
      mScratch128i[5] = 14;
      mScratch128i[6] = 17;
      mScratch128i[7] = 19;
      mScratch128i[8] = 21;
      mScratch128i[9] = 22;
      mScratch128i[10] = 24;
      mScratch128i[11] = 27;
      mScratch128i[12] = 30;
      mScratch128i[13] = 49;
      mScratch128i[14] = 56;
      mScratch128i[15] = 59;
      mScratch128i[16] = 140;
      mScratch128i[17] = 143;
      mScratch128i[18] = 145;
      mScratch128i[19] = 146;
      mScratch128i[20] = 154;
      mScratch128i[21] = 155;
      mScratch128i[22] = 158;
      mScratch128i[23] = 161;
      mScratch128i[24] = 162;
      mScratch128i[25] = 163;
      mScratch128i[26] = 165;
      mScratch128i[27] = 169;
      mScratch128i[28] = 171;
      mScratch128i[29] = 172;
      mScratch128i[30] = 246;
      mScratch128i[31] = 247;
      mScratch128i[32] = 249;
      mScratch128i[33] = 250;
      mScratch128i[34] = 252;
      mScratch128i[35] = 256;
      mScratch128i[36] = 258;
      mScratch128i[37] = 261;
      mScratch128i[38] = 391;
      mScratch128i[39] = 397;
      mScratch128i[40] = 402;
      mScratch128i[41] = 408;
      mScratch128i[42] = 410;
      mScratch128i[43] = 411;
      mScratch128i[44] = 412;
      mScratch128i[45] = 415;
      mScratch128i[46] = 416;
      mScratch128i[47] = 417;
      mScratch128i[48] = 419;
      mScratch128i[49] = 448;
      mScratch128i[50] = 455;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<51; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //069 023 {-2.057164, -0.999380, -0.532565}
    //075 025 {-2.057164, 0.985557, -0.532565}
    //084 028 {-2.057164, -0.999380, -0.532565}
    //096 032 {-2.057164, -0.999380, -0.532565}
    //753 251 {-2.057164, 0.985557, -0.532565}
    //762 254 {-2.057164, 0.985557, -0.532565}
    //771 257 {-2.057164, 0.985557, -0.532565}
    //780 260 {-2.057164, -0.999380, -0.532565}
    //786 262 {-2.057164, -0.999380, -0.532565}
    //873 291 {-2.057164, 0.985557, -0.532565}
    //891 297 {-2.057164, -0.999380, -0.532565}
    //1239 413 {-2.057164, -0.999380, -0.532565}
    //1254 418 {-2.057164, 0.985557, -0.532565}
    synchronized (mScratch128i) {
      mScratch128i[0] = 23;
      mScratch128i[1] = 25;
      mScratch128i[2] = 28;
      mScratch128i[3] = 32;
      mScratch128i[4] = 251;
      mScratch128i[5] = 254;
      mScratch128i[6] = 257;
      mScratch128i[7] = 260;
      mScratch128i[8] = 262;
      mScratch128i[9] = 291;
      mScratch128i[10] = 297;
      mScratch128i[11] = 413;
      mScratch128i[12] = 418;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<13; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //447 149 {-1.979976, -0.525535, -0.400067}
    //453 151 {-1.979976, 0.525533, -0.400068}
    //471 157 {-1.979976, -0.525535, -0.400067}
    //477 159 {-1.979976, 0.525533, -0.400068}
    //1335 445 {-1.979976, 0.525533, -0.400068}
    //1341 447 {-1.979976, 0.525533, -0.400068}
    //1350 450 {-1.979976, -0.525535, -0.400067}
    //1359 453 {-1.979976, -0.525535, -0.400067}
    //1374 458 {-1.979976, 0.525533, -0.400068}
    //1383 461 {-1.979976, 0.525533, -0.400068}
    //1392 464 {-1.979976, -0.525535, -0.400067}
    //1398 466 {-1.979976, -0.525535, -0.400067}
    synchronized (mScratch128i) {
      mScratch128i[0] = 149;
      mScratch128i[1] = 151;
      mScratch128i[2] = 157;
      mScratch128i[3] = 159;
      mScratch128i[4] = 445;
      mScratch128i[5] = 447;
      mScratch128i[6] = 450;
      mScratch128i[7] = 453;
      mScratch128i[8] = 458;
      mScratch128i[9] = 461;
      mScratch128i[10] = 464;
      mScratch128i[11] = 466;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<12; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //027 009 {-1.848047, 0.952779, -0.506540}
    //039 013 {-1.850482, -0.964935, -0.506943}
    //060 020 {-1.848047, 0.952779, -0.506540}
    //078 026 {-1.848047, 0.952779, -0.506540}
    //087 029 {-1.850482, -0.964935, -0.506943}
    //093 031 {-1.848143, -0.964399, -0.514271}
    //150 050 {-1.848047, 0.952779, -0.506540}
    //156 052 {-1.848047, 0.952779, -0.506540}
    //174 058 {-1.850482, -0.964935, -0.506943}
    //468 156 {-1.850482, -0.964935, -0.506943}
    //480 160 {-1.848047, 0.952779, -0.506540}
    //759 253 {-1.848143, 0.895017, -0.569752}
    //765 255 {-1.848047, 0.952779, -0.506540}
    //777 259 {-1.850482, -0.964935, -0.506943}
    //789 263 {-1.848143, -0.908845, -0.569752}
    //804 268 {-1.848047, 0.952779, -0.506540}
    //819 273 {-1.850482, -0.964935, -0.506943}
    //876 292 {-1.848143, 0.895017, -0.569752}
    //879 293 {-1.848047, 0.952779, -0.506540}
    //885 295 {-1.848047, 0.952779, -0.506540}
    //888 296 {-1.848143, 0.895017, -0.569752}
    //894 298 {-1.848143, -0.964399, -0.514271}
    //897 299 {-1.848143, -0.908845, -0.569752}
    //903 301 {-1.848143, -0.908845, -0.569752}
    //906 302 {-1.850482, -0.964935, -0.506943}
    //1176 392 {-1.848143, -0.908845, -0.569752}
    //1182 394 {-1.848143, -0.908845, -0.569752}
    //1194 398 {-1.850482, -0.964935, -0.506943}
    //1200 400 {-1.850482, -0.964935, -0.506943}
    //1212 404 {-1.848143, 0.895017, -0.569752}
    //1221 407 {-1.848143, 0.895017, -0.569752}
    //1380 460 {-1.848047, 0.952779, -0.506540}
    //1401 467 {-1.850482, -0.964935, -0.506943}
    synchronized (mScratch128i) {
      mScratch128i[0] = 9;
      mScratch128i[1] = 13;
      mScratch128i[2] = 20;
      mScratch128i[3] = 26;
      mScratch128i[4] = 29;
      mScratch128i[5] = 31;
      mScratch128i[6] = 50;
      mScratch128i[7] = 52;
      mScratch128i[8] = 58;
      mScratch128i[9] = 156;
      mScratch128i[10] = 160;
      mScratch128i[11] = 253;
      mScratch128i[12] = 255;
      mScratch128i[13] = 259;
      mScratch128i[14] = 263;
      mScratch128i[15] = 268;
      mScratch128i[16] = 273;
      mScratch128i[17] = 292;
      mScratch128i[18] = 293;
      mScratch128i[19] = 295;
      mScratch128i[20] = 296;
      mScratch128i[21] = 298;
      mScratch128i[22] = 299;
      mScratch128i[23] = 301;
      mScratch128i[24] = 302;
      mScratch128i[25] = 392;
      mScratch128i[26] = 394;
      mScratch128i[27] = 398;
      mScratch128i[28] = 400;
      mScratch128i[29] = 404;
      mScratch128i[30] = 407;
      mScratch128i[31] = 460;
      mScratch128i[32] = 467;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<33; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //045 015 {-1.691378, 0.331979, -0.478952}
    //048 016 {-1.691378, 0.295123, -0.559611}
    //054 018 {-1.691378, 0.331979, -0.478952}
    //144 048 {-1.691378, 0.331979, -0.478952}
    //153 051 {-1.691378, 0.331979, -0.478952}
    //159 053 {-1.615306, 0.865790, -0.486102}
    //162 054 {-1.691378, -0.326782, -0.495731}
    //165 055 {-1.691378, -0.336714, -0.425374}
    //171 057 {-1.691378, -0.336714, -0.425374}
    //225 075 {-1.679747, 0.021068, -0.177832}
    //237 079 {-1.679747, 0.021068, -0.177832}
    //330 110 {-1.691378, 0.295123, -0.559611}
    //339 113 {-1.691378, -0.326782, -0.495731}
    //348 116 {-1.691378, -0.326782, -0.495731}
    //354 118 {-1.691378, -0.336714, -0.425374}
    //357 119 {-1.691378, -0.326782, -0.495731}
    //363 121 {-1.691378, -0.274322, -0.288445}
    //366 122 {-1.691378, -0.336714, -0.425374}
    //375 125 {-1.691378, -0.274322, -0.288445}
    //381 127 {-1.679747, 0.021068, -0.177832}
    //384 128 {-1.691378, -0.274322, -0.288445}
    //387 129 {-1.679747, 0.021068, -0.177832}
    //393 131 {-1.691378, 0.274321, -0.288445}
    //402 134 {-1.691378, 0.274321, -0.288445}
    //408 136 {-1.691378, 0.331979, -0.478952}
    //411 137 {-1.691378, 0.274321, -0.288445}
    //414 138 {-1.691378, 0.295123, -0.559611}
    //426 142 {-1.691378, -0.326782, -0.495731}
    //432 144 {-1.691378, -0.326782, -0.495731}
    //441 147 {-1.691378, -0.274322, -0.288445}
    //444 148 {-1.679747, 0.021068, -0.177832}
    //450 150 {-1.691378, 0.274321, -0.288445}
    //456 152 {-1.679747, 0.021068, -0.177832}
    //459 153 {-1.691378, 0.295123, -0.559611}
    //792 264 {-1.691378, 0.295123, -0.559611}
    //795 265 {-1.691378, 0.331979, -0.478952}
    //798 266 {-1.615306, 0.865790, -0.486102}
    //801 267 {-1.691378, 0.331979, -0.478952}
    //807 269 {-1.615306, 0.865790, -0.486102}
    //810 270 {-1.691378, -0.336714, -0.425374}
    //813 271 {-1.691378, -0.326782, -0.495731}
    //816 272 {-1.615306, -0.879617, -0.486102}
    //822 274 {-1.691378, -0.336714, -0.425374}
    //825 275 {-1.615306, -0.879617, -0.486102}
    //882 294 {-1.615306, 0.865790, -0.486102}
    //900 300 {-1.615306, -0.879617, -0.486102}
    //1170 390 {-1.691378, -0.326782, -0.495731}
    //1179 393 {-1.691378, -0.326782, -0.495731}
    //1185 395 {-1.615306, -0.879617, -0.486102}
    //1188 396 {-1.691378, -0.326782, -0.495731}
    //1197 399 {-1.691378, -0.326782, -0.495731}
    //1203 401 {-1.615306, -0.879617, -0.486102}
    //1209 403 {-1.691378, 0.295123, -0.559611}
    //1215 405 {-1.691378, 0.295123, -0.559611}
    //1218 406 {-1.615306, 0.865790, -0.486102}
    //1332 444 {-1.679747, 0.021068, -0.177832}
    //1353 451 {-1.679747, 0.021068, -0.177832}
    //1368 456 {-1.691378, 0.274321, -0.288445}
    //1371 457 {-1.691378, 0.331979, -0.478952}
    //1377 459 {-1.691378, 0.331979, -0.478952}
    //1386 462 {-1.691378, -0.336714, -0.425374}
    //1389 463 {-1.691378, -0.274322, -0.288445}
    //1395 465 {-1.691378, -0.336714, -0.425374}
    //1410 470 {-1.691378, 0.331979, -0.478952}
    //1416 472 {-1.691378, 0.295123, -0.559611}
    //1419 473 {-1.691378, 0.331979, -0.478952}
    synchronized (mScratch128i) {
      mScratch128i[0] = 15;
      mScratch128i[1] = 16;
      mScratch128i[2] = 18;
      mScratch128i[3] = 48;
      mScratch128i[4] = 51;
      mScratch128i[5] = 53;
      mScratch128i[6] = 54;
      mScratch128i[7] = 55;
      mScratch128i[8] = 57;
      mScratch128i[9] = 75;
      mScratch128i[10] = 79;
      mScratch128i[11] = 110;
      mScratch128i[12] = 113;
      mScratch128i[13] = 116;
      mScratch128i[14] = 118;
      mScratch128i[15] = 119;
      mScratch128i[16] = 121;
      mScratch128i[17] = 122;
      mScratch128i[18] = 125;
      mScratch128i[19] = 127;
      mScratch128i[20] = 128;
      mScratch128i[21] = 129;
      mScratch128i[22] = 131;
      mScratch128i[23] = 134;
      mScratch128i[24] = 136;
      mScratch128i[25] = 137;
      mScratch128i[26] = 138;
      mScratch128i[27] = 142;
      mScratch128i[28] = 144;
      mScratch128i[29] = 147;
      mScratch128i[30] = 148;
      mScratch128i[31] = 150;
      mScratch128i[32] = 152;
      mScratch128i[33] = 153;
      mScratch128i[34] = 264;
      mScratch128i[35] = 265;
      mScratch128i[36] = 266;
      mScratch128i[37] = 267;
      mScratch128i[38] = 269;
      mScratch128i[39] = 270;
      mScratch128i[40] = 271;
      mScratch128i[41] = 272;
      mScratch128i[42] = 274;
      mScratch128i[43] = 275;
      mScratch128i[44] = 294;
      mScratch128i[45] = 300;
      mScratch128i[46] = 390;
      mScratch128i[47] = 393;
      mScratch128i[48] = 395;
      mScratch128i[49] = 396;
      mScratch128i[50] = 399;
      mScratch128i[51] = 401;
      mScratch128i[52] = 403;
      mScratch128i[53] = 405;
      mScratch128i[54] = 406;
      mScratch128i[55] = 444;
      mScratch128i[56] = 451;
      mScratch128i[57] = 456;
      mScratch128i[58] = 457;
      mScratch128i[59] = 459;
      mScratch128i[60] = 462;
      mScratch128i[61] = 463;
      mScratch128i[62] = 465;
      mScratch128i[63] = 470;
      mScratch128i[64] = 472;
      mScratch128i[65] = 473;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<66; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //099 033 {-1.030350, -0.317038, -0.451115}
    //111 037 {-1.030350, 0.317036, -0.451116}
    //180 060 {-1.030350, -0.317038, -0.451115}
    //192 064 {-1.030350, 0.317036, -0.451116}
    //201 067 {-1.030350, 0.317036, -0.451116}
    //207 069 {-1.030350, -0.317038, -0.451115}
    //324 108 {-1.030350, 0.317036, -0.451116}
    //336 112 {-1.030350, -0.317038, -0.451115}
    //342 114 {-1.030350, -0.317038, -0.451115}
    //828 276 {-1.030350, -0.317038, -0.451115}
    //945 315 {-1.030350, 0.317036, -0.451116}
    //1137 379 {-1.030350, -0.317038, -0.451115}
    //1152 384 {-1.030350, 0.317036, -0.451116}
    //1407 469 {-1.030350, 0.317036, -0.451116}
    //1413 471 {-1.030350, 0.317036, -0.451116}
    synchronized (mScratch128i) {
      mScratch128i[0] = 33;
      mScratch128i[1] = 37;
      mScratch128i[2] = 60;
      mScratch128i[3] = 64;
      mScratch128i[4] = 67;
      mScratch128i[5] = 69;
      mScratch128i[6] = 108;
      mScratch128i[7] = 112;
      mScratch128i[8] = 114;
      mScratch128i[9] = 276;
      mScratch128i[10] = 315;
      mScratch128i[11] = 379;
      mScratch128i[12] = 384;
      mScratch128i[13] = 469;
      mScratch128i[14] = 471;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<15; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //228 076 {-0.955447, -0.288134, 0.002265}
    //234 078 {-0.955447, 0.288132, 0.002265}
    //372 124 {-0.955447, -0.288134, 0.002265}
    //378 126 {-0.955447, -0.288134, 0.002265}
    //390 130 {-0.955447, 0.288132, 0.002265}
    //396 132 {-0.955447, 0.288132, 0.002265}
    //1695 565 {-0.955447, 0.288132, 0.002265}
    //1701 567 {-0.955447, 0.288132, 0.002265}
    //1710 570 {-0.955447, 0.288132, 0.002265}
    //1731 577 {-0.955447, -0.288134, 0.002265}
    //1746 582 {-0.955447, -0.288134, 0.002265}
    //1755 585 {-0.955447, -0.288134, 0.002265}
    synchronized (mScratch128i) {
      mScratch128i[0] = 76;
      mScratch128i[1] = 78;
      mScratch128i[2] = 124;
      mScratch128i[3] = 126;
      mScratch128i[4] = 130;
      mScratch128i[5] = 132;
      mScratch128i[6] = 565;
      mScratch128i[7] = 567;
      mScratch128i[8] = 570;
      mScratch128i[9] = 577;
      mScratch128i[10] = 582;
      mScratch128i[11] = 585;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<12; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //186 062 {-0.834075, -0.410472, -0.214108}
    //195 065 {-0.834075, 0.410471, -0.214106}
    //216 072 {-0.834075, -0.410472, -0.214108}
    //246 082 {-0.834075, 0.410471, -0.214106}
    //345 115 {-0.834075, -0.410472, -0.214108}
    //351 117 {-0.834075, -0.410472, -0.214108}
    //360 120 {-0.834075, -0.410472, -0.214108}
    //369 123 {-0.834075, -0.410472, -0.214108}
    //399 133 {-0.834075, 0.410471, -0.214106}
    //405 135 {-0.834075, 0.410471, -0.214106}
    //1404 468 {-0.834075, 0.410471, -0.214106}
    //1677 559 {-0.834075, 0.410471, -0.214106}
    //1692 564 {-0.834075, 0.410471, -0.214106}
    //1749 583 {-0.834075, -0.410472, -0.214108}
    //1764 588 {-0.834075, -0.410472, -0.214108}
    synchronized (mScratch128i) {
      mScratch128i[0] = 62;
      mScratch128i[1] = 65;
      mScratch128i[2] = 72;
      mScratch128i[3] = 82;
      mScratch128i[4] = 115;
      mScratch128i[5] = 117;
      mScratch128i[6] = 120;
      mScratch128i[7] = 123;
      mScratch128i[8] = 133;
      mScratch128i[9] = 135;
      mScratch128i[10] = 468;
      mScratch128i[11] = 559;
      mScratch128i[12] = 564;
      mScratch128i[13] = 583;
      mScratch128i[14] = 588;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<15; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //198 066 {-0.745866, -0.000001, -0.533056}
    //210 070 {-0.745866, -0.000001, -0.533056}
    //327 109 {-0.745866, -0.000001, -0.533056}
    //333 111 {-0.745866, -0.000001, -0.533056}
    //417 139 {-0.745866, -0.000001, -0.533056}
    //423 141 {-0.745866, -0.000001, -0.533056}
    //1785 595 {-0.745866, -0.000001, -0.533056}
    //1800 600 {-0.745866, -0.000001, -0.533056}
    synchronized (mScratch128i) {
      mScratch128i[0] = 66;
      mScratch128i[1] = 70;
      mScratch128i[2] = 109;
      mScratch128i[3] = 111;
      mScratch128i[4] = 139;
      mScratch128i[5] = 141;
      mScratch128i[6] = 595;
      mScratch128i[7] = 600;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //102 034 {-0.672170, -0.401866, -0.305382}
    //105 035 {-0.672170, -0.401866, -0.305382}
    //108 036 {-0.672170, 0.401867, -0.305379}
    //114 038 {-0.672170, 0.401867, -0.305379}
    //120 040 {-0.672170, 0.401867, -0.305379}
    //123 041 {-0.672170, 0.401867, -0.305379}
    //183 061 {-0.672170, -0.401866, -0.305382}
    //189 063 {-0.672170, 0.401867, -0.305379}
    //219 073 {-0.672170, -0.401866, -0.305382}
    //243 081 {-0.672170, 0.401867, -0.305379}
    //831 277 {-0.672170, -0.401866, -0.305382}
    //951 317 {-0.672170, 0.401867, -0.305379}
    //969 323 {-0.672170, -0.401866, -0.305382}
    //975 325 {-0.672170, 0.401867, -0.305379}
    synchronized (mScratch128i) {
      mScratch128i[0] = 34;
      mScratch128i[1] = 35;
      mScratch128i[2] = 36;
      mScratch128i[3] = 38;
      mScratch128i[4] = 40;
      mScratch128i[5] = 41;
      mScratch128i[6] = 61;
      mScratch128i[7] = 63;
      mScratch128i[8] = 73;
      mScratch128i[9] = 81;
      mScratch128i[10] = 277;
      mScratch128i[11] = 317;
      mScratch128i[12] = 323;
      mScratch128i[13] = 325;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<14; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //117 039 {-0.419499, 0.341208, -0.270369}
    //204 068 {-0.419499, 0.341208, -0.270369}
    //213 071 {-0.419499, -0.341208, -0.270371}
    //222 074 {-0.419499, -0.341208, -0.270371}
    //249 083 {-0.419499, 0.341208, -0.270369}
    //834 278 {-0.401885, -0.901074, -0.362554}
    //948 316 {-0.401885, 0.919354, -0.362554}
    //954 318 {-0.419499, -0.341208, -0.270371}
    //960 320 {-0.401885, -0.901074, -0.362554}
    //963 321 {-0.419499, -0.341208, -0.270371}
    //966 322 {-0.401885, -0.901074, -0.362554}
    //972 324 {-0.419499, 0.341208, -0.270369}
    //978 326 {-0.401885, 0.919354, -0.362554}
    //981 327 {-0.419499, 0.341208, -0.270369}
    //984 328 {-0.401885, 0.919354, -0.362554}
    //1134 378 {-0.419499, -0.341208, -0.270371}
    //1140 380 {-0.401885, -0.901074, -0.362554}
    //1143 381 {-0.419499, -0.341208, -0.270371}
    //1146 382 {-0.401885, -0.901074, -0.362554}
    //1155 385 {-0.419499, 0.341208, -0.270369}
    //1158 386 {-0.401885, 0.919354, -0.362554}
    //1161 387 {-0.419499, 0.341208, -0.270369}
    //1167 389 {-0.401885, 0.919354, -0.362554}
    //1674 558 {-0.419499, 0.341208, -0.270369}
    //1683 561 {-0.419499, 0.341208, -0.270369}
    //1767 589 {-0.419499, -0.341208, -0.270371}
    //1773 591 {-0.419499, -0.341208, -0.270371}
    //1782 594 {-0.419499, -0.341208, -0.270371}
    //1791 597 {-0.419499, -0.341208, -0.270371}
    //1803 601 {-0.419499, 0.341208, -0.270369}
    //1809 603 {-0.419499, 0.341208, -0.270369}
    synchronized (mScratch128i) {
      mScratch128i[0] = 39;
      mScratch128i[1] = 68;
      mScratch128i[2] = 71;
      mScratch128i[3] = 74;
      mScratch128i[4] = 83;
      mScratch128i[5] = 278;
      mScratch128i[6] = 316;
      mScratch128i[7] = 318;
      mScratch128i[8] = 320;
      mScratch128i[9] = 321;
      mScratch128i[10] = 322;
      mScratch128i[11] = 324;
      mScratch128i[12] = 326;
      mScratch128i[13] = 327;
      mScratch128i[14] = 328;
      mScratch128i[15] = 378;
      mScratch128i[16] = 380;
      mScratch128i[17] = 381;
      mScratch128i[18] = 382;
      mScratch128i[19] = 385;
      mScratch128i[20] = 386;
      mScratch128i[21] = 387;
      mScratch128i[22] = 389;
      mScratch128i[23] = 558;
      mScratch128i[24] = 561;
      mScratch128i[25] = 589;
      mScratch128i[26] = 591;
      mScratch128i[27] = 594;
      mScratch128i[28] = 597;
      mScratch128i[29] = 601;
      mScratch128i[30] = 603;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<31; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //231 077 {-0.380746, 0.005540, 0.332009}
    //240 080 {-0.380746, 0.005540, 0.332009}
    //840 280 {-0.380746, 0.005540, 0.332009}
    //846 282 {-0.380746, 0.005540, 0.332009}
    //1713 571 {-0.380746, 0.005540, 0.332009}
    //1719 573 {-0.380746, 0.005540, 0.332009}
    //1728 576 {-0.380746, 0.005540, 0.332009}
    //1737 579 {-0.380746, 0.005540, 0.332009}
    synchronized (mScratch128i) {
      mScratch128i[0] = 77;
      mScratch128i[1] = 80;
      mScratch128i[2] = 280;
      mScratch128i[3] = 282;
      mScratch128i[4] = 571;
      mScratch128i[5] = 573;
      mScratch128i[6] = 576;
      mScratch128i[7] = 579;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //264 088 {-0.018309, 0.095874, 0.443346}
    //837 279 {-0.018309, 0.095874, 0.443346}
    //843 281 {-0.006361, -0.000006, 1.229876}
    //852 284 {-0.006361, -0.000006, 1.229876}
    //1104 368 {-0.006361, -0.000006, 1.229876}
    //1113 371 {-0.006361, -0.000006, 1.229876}
    //1119 373 {-0.018309, 0.095874, 0.443346}
    //1122 374 {-0.006361, -0.000006, 1.229876}
    //1128 376 {-0.006361, -0.000006, 1.229876}
    //1722 574 {-0.018309, 0.095874, 0.443346}
    synchronized (mScratch128i) {
      mScratch128i[0] = 88;
      mScratch128i[1] = 279;
      mScratch128i[2] = 281;
      mScratch128i[3] = 284;
      mScratch128i[4] = 368;
      mScratch128i[5] = 371;
      mScratch128i[6] = 373;
      mScratch128i[7] = 374;
      mScratch128i[8] = 376;
      mScratch128i[9] = 574;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //252 084 {0.043326, -0.109961, 0.460562}
    //849 283 {0.043326, -0.109961, 0.460562}
    //957 319 {0.001005, -1.110966, -0.352525}
    //987 329 {0.001005, 1.129249, -0.352524}
    //1098 366 {0.043326, -0.109961, 0.460562}
    //1149 383 {0.001005, -1.110966, -0.352525}
    //1164 388 {0.001005, 1.129249, -0.352524}
    //1743 581 {0.043326, -0.109961, 0.460562}
    synchronized (mScratch128i) {
      mScratch128i[0] = 84;
      mScratch128i[1] = 283;
      mScratch128i[2] = 319;
      mScratch128i[3] = 329;
      mScratch128i[4] = 366;
      mScratch128i[5] = 383;
      mScratch128i[6] = 388;
      mScratch128i[7] = 581;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1110 370 {0.243681, -0.000006, 1.610966}
    //1131 377 {0.243681, -0.000006, 1.610966}
    synchronized (mScratch128i) {
      mScratch128i[0] = 370;
      mScratch128i[1] = 377;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<2; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //255 085 {0.372936, -0.307561, 0.440863}
    //261 087 {0.372936, 0.307558, 0.440865}
    //1530 510 {0.372936, 0.361653, -0.100691}
    //1533 511 {0.372936, 0.428667, 0.137185}
    //1539 513 {0.372936, 0.361653, -0.100691}
    //1548 516 {0.372936, 0.428667, 0.137185}
    //1551 517 {0.372936, 0.307558, 0.440865}
    //1557 519 {0.372936, 0.307558, 0.440865}
    //1566 522 {0.372936, 0.307558, 0.440865}
    //1587 529 {0.372936, -0.307561, 0.440863}
    //1602 534 {0.372936, -0.307561, 0.440863}
    //1605 535 {0.372936, -0.428668, 0.137182}
    //1611 537 {0.372936, -0.307561, 0.440863}
    //1620 540 {0.372936, -0.428668, 0.137182}
    //1623 541 {0.372936, -0.361653, -0.100694}
    //1629 543 {0.372936, -0.361653, -0.100694}
    //1638 546 {0.372936, -0.361653, -0.100694}
    //1641 547 {0.372936, -0.000001, -0.272062}
    //1647 549 {0.372936, -0.000001, -0.272062}
    //1656 552 {0.372936, -0.000001, -0.272062}
    //1659 553 {0.372936, 0.361653, -0.100691}
    //1665 555 {0.372936, -0.000001, -0.272062}
    //1680 560 {0.372936, 0.428667, 0.137185}
    //1686 562 {0.372936, 0.428667, 0.137185}
    //1689 563 {0.372936, 0.361653, -0.100691}
    //1698 566 {0.372936, 0.428667, 0.137185}
    //1704 568 {0.372936, 0.307558, 0.440865}
    //1707 569 {0.372936, 0.428667, 0.137185}
    //1716 572 {0.372936, 0.307558, 0.440865}
    //1725 575 {0.372936, 0.307558, 0.440865}
    //1734 578 {0.372936, -0.307561, 0.440863}
    //1740 580 {0.372936, -0.307561, 0.440863}
    //1752 584 {0.372936, -0.428668, 0.137182}
    //1758 586 {0.372936, -0.428668, 0.137182}
    //1761 587 {0.372936, -0.307561, 0.440863}
    //1770 590 {0.372936, -0.428668, 0.137182}
    //1776 592 {0.372936, -0.361653, -0.100694}
    //1779 593 {0.372936, -0.428668, 0.137182}
    //1788 596 {0.372936, -0.000001, -0.272062}
    //1794 598 {0.372936, -0.000001, -0.272062}
    //1797 599 {0.372936, -0.361653, -0.100694}
    //1806 602 {0.372936, -0.000001, -0.272062}
    //1812 604 {0.372936, 0.361653, -0.100691}
    //1815 605 {0.372936, -0.000001, -0.272062}
    synchronized (mScratch128i) {
      mScratch128i[0] = 85;
      mScratch128i[1] = 87;
      mScratch128i[2] = 510;
      mScratch128i[3] = 511;
      mScratch128i[4] = 513;
      mScratch128i[5] = 516;
      mScratch128i[6] = 517;
      mScratch128i[7] = 519;
      mScratch128i[8] = 522;
      mScratch128i[9] = 529;
      mScratch128i[10] = 534;
      mScratch128i[11] = 535;
      mScratch128i[12] = 537;
      mScratch128i[13] = 540;
      mScratch128i[14] = 541;
      mScratch128i[15] = 543;
      mScratch128i[16] = 546;
      mScratch128i[17] = 547;
      mScratch128i[18] = 549;
      mScratch128i[19] = 552;
      mScratch128i[20] = 553;
      mScratch128i[21] = 555;
      mScratch128i[22] = 560;
      mScratch128i[23] = 562;
      mScratch128i[24] = 563;
      mScratch128i[25] = 566;
      mScratch128i[26] = 568;
      mScratch128i[27] = 569;
      mScratch128i[28] = 572;
      mScratch128i[29] = 575;
      mScratch128i[30] = 578;
      mScratch128i[31] = 580;
      mScratch128i[32] = 584;
      mScratch128i[33] = 586;
      mScratch128i[34] = 587;
      mScratch128i[35] = 590;
      mScratch128i[36] = 592;
      mScratch128i[37] = 593;
      mScratch128i[38] = 596;
      mScratch128i[39] = 598;
      mScratch128i[40] = 599;
      mScratch128i[41] = 602;
      mScratch128i[42] = 604;
      mScratch128i[43] = 605;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<44; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //258 086 {0.517643, 0.010364, 0.583554}
    //267 089 {0.517643, 0.010364, 0.583554}
    //1101 367 {0.517643, 0.010364, 0.583554}
    //1107 369 {0.517643, 0.010364, 0.583554}
    //1116 372 {0.517643, 0.010364, 0.583554}
    //1125 375 {0.517643, 0.010364, 0.583554}
    //1569 523 {0.517643, 0.010364, 0.583554}
    //1575 525 {0.517643, 0.010364, 0.583554}
    //1584 528 {0.517643, 0.010364, 0.583554}
    //1593 531 {0.517643, 0.010364, 0.583554}
    synchronized (mScratch128i) {
      mScratch128i[0] = 86;
      mScratch128i[1] = 89;
      mScratch128i[2] = 367;
      mScratch128i[3] = 369;
      mScratch128i[4] = 372;
      mScratch128i[5] = 375;
      mScratch128i[6] = 523;
      mScratch128i[7] = 525;
      mScratch128i[8] = 528;
      mScratch128i[9] = 531;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //309 103 {1.211700, -0.326537, 0.283738}
    //315 105 {1.211700, 0.326536, 0.283741}
    //1425 475 {1.211700, 0.326536, 0.283741}
    //1476 492 {1.211700, -0.326537, 0.283738}
    //1536 512 {1.211700, 0.326536, 0.283741}
    //1542 514 {1.211700, 0.326536, 0.283741}
    //1554 518 {1.211700, 0.326536, 0.283741}
    //1563 521 {1.211700, 0.326536, 0.283741}
    //1608 536 {1.211700, -0.326537, 0.283738}
    //1614 538 {1.211700, -0.326537, 0.283738}
    //1626 542 {1.211700, -0.326537, 0.283738}
    //1635 545 {1.211700, -0.326537, 0.283738}
    synchronized (mScratch128i) {
      mScratch128i[0] = 103;
      mScratch128i[1] = 105;
      mScratch128i[2] = 475;
      mScratch128i[3] = 492;
      mScratch128i[4] = 512;
      mScratch128i[5] = 514;
      mScratch128i[6] = 518;
      mScratch128i[7] = 521;
      mScratch128i[8] = 536;
      mScratch128i[9] = 538;
      mScratch128i[10] = 542;
      mScratch128i[11] = 545;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<12; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //126 042 {1.334634, -0.247983, 0.106300}
    //129 043 {1.385885, -0.199580, 0.052925}
    //135 045 {1.385885, 0.199578, 0.052925}
    //138 046 {1.334634, 0.247982, 0.106301}
    //270 090 {1.389904, -0.000001, 0.001955}
    //273 091 {1.385885, 0.199578, 0.052925}
    //279 093 {1.385885, -0.199580, 0.052925}
    //282 094 {1.389904, -0.000001, 0.001955}
    //291 097 {1.334634, 0.247982, 0.106301}
    //297 099 {1.334634, -0.247983, 0.106300}
    //306 102 {1.317551, -0.221218, 0.526235}
    //318 106 {1.317551, 0.221215, 0.526237}
    //909 303 {1.385885, -0.199580, 0.052925}
    //912 304 {1.334634, -0.247983, 0.106300}
    //915 305 {1.389904, -0.000001, 0.001955}
    //918 306 {1.385885, -0.199580, 0.052925}
    //921 307 {1.334634, -0.247983, 0.106300}
    //927 309 {1.334634, 0.247982, 0.106301}
    //930 310 {1.385885, 0.199578, 0.052925}
    //933 311 {1.389904, -0.000001, 0.001955}
    //936 312 {1.385885, 0.199578, 0.052925}
    //939 313 {1.334634, 0.247982, 0.106301}
    //993 331 {1.334634, 0.247982, 0.106301}
    //1011 337 {1.334634, -0.247983, 0.106300}
    //1029 343 {1.385885, -0.199580, 0.052925}
    //1047 349 {1.385885, 0.199578, 0.052925}
    //1422 474 {1.334634, 0.247982, 0.106301}
    //1431 477 {1.334634, 0.247982, 0.106301}
    //1440 480 {1.317551, 0.221215, 0.526237}
    //1443 481 {1.317551, -0.000002, 0.610136}
    //1449 483 {1.317551, 0.221215, 0.526237}
    //1458 486 {1.317551, -0.000002, 0.610136}
    //1461 487 {1.317551, -0.221218, 0.526235}
    //1467 489 {1.317551, -0.221218, 0.526235}
    //1479 493 {1.334634, -0.247983, 0.106300}
    //1485 495 {1.334634, -0.247983, 0.106300}
    //1497 499 {1.389904, -0.000001, 0.001955}
    //1512 504 {1.389904, -0.000001, 0.001955}
    //1545 515 {1.334634, 0.247982, 0.106301}
    //1560 520 {1.317551, 0.221215, 0.526237}
    //1572 524 {1.317551, 0.221215, 0.526237}
    //1578 526 {1.317551, -0.000002, 0.610136}
    //1581 527 {1.317551, 0.221215, 0.526237}
    //1590 530 {1.317551, -0.221218, 0.526235}
    //1596 532 {1.317551, -0.221218, 0.526235}
    //1599 533 {1.317551, -0.000002, 0.610136}
    //1617 539 {1.317551, -0.221218, 0.526235}
    //1632 544 {1.334634, -0.247983, 0.106300}
    //1644 548 {1.334634, -0.247983, 0.106300}
    //1650 550 {1.389904, -0.000001, 0.001955}
    //1653 551 {1.334634, -0.247983, 0.106300}
    //1662 554 {1.334634, 0.247982, 0.106301}
    //1668 556 {1.334634, 0.247982, 0.106301}
    //1671 557 {1.389904, -0.000001, 0.001955}
    synchronized (mScratch128i) {
      mScratch128i[0] = 42;
      mScratch128i[1] = 43;
      mScratch128i[2] = 45;
      mScratch128i[3] = 46;
      mScratch128i[4] = 90;
      mScratch128i[5] = 91;
      mScratch128i[6] = 93;
      mScratch128i[7] = 94;
      mScratch128i[8] = 97;
      mScratch128i[9] = 99;
      mScratch128i[10] = 102;
      mScratch128i[11] = 106;
      mScratch128i[12] = 303;
      mScratch128i[13] = 304;
      mScratch128i[14] = 305;
      mScratch128i[15] = 306;
      mScratch128i[16] = 307;
      mScratch128i[17] = 309;
      mScratch128i[18] = 310;
      mScratch128i[19] = 311;
      mScratch128i[20] = 312;
      mScratch128i[21] = 313;
      mScratch128i[22] = 331;
      mScratch128i[23] = 337;
      mScratch128i[24] = 343;
      mScratch128i[25] = 349;
      mScratch128i[26] = 474;
      mScratch128i[27] = 477;
      mScratch128i[28] = 480;
      mScratch128i[29] = 481;
      mScratch128i[30] = 483;
      mScratch128i[31] = 486;
      mScratch128i[32] = 487;
      mScratch128i[33] = 489;
      mScratch128i[34] = 493;
      mScratch128i[35] = 495;
      mScratch128i[36] = 499;
      mScratch128i[37] = 504;
      mScratch128i[38] = 515;
      mScratch128i[39] = 520;
      mScratch128i[40] = 524;
      mScratch128i[41] = 526;
      mScratch128i[42] = 527;
      mScratch128i[43] = 530;
      mScratch128i[44] = 532;
      mScratch128i[45] = 533;
      mScratch128i[46] = 539;
      mScratch128i[47] = 544;
      mScratch128i[48] = 548;
      mScratch128i[49] = 550;
      mScratch128i[50] = 551;
      mScratch128i[51] = 554;
      mScratch128i[52] = 556;
      mScratch128i[53] = 557;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<54; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //924 308 {1.524988, -0.327476, 0.013182}
    //942 314 {1.524988, 0.327474, 0.013181}
    //990 330 {1.524988, 0.327474, 0.013181}
    //999 333 {1.524988, 0.327474, 0.013181}
    //1008 336 {1.524988, -0.327476, 0.013182}
    //1017 339 {1.524988, -0.327476, 0.013182}
    //1032 344 {1.524988, -0.327476, 0.013182}
    //1038 346 {1.524988, -0.327476, 0.013182}
    //1050 350 {1.524988, 0.327474, 0.013181}
    //1056 352 {1.524988, 0.327474, 0.013181}
    synchronized (mScratch128i) {
      mScratch128i[0] = 308;
      mScratch128i[1] = 314;
      mScratch128i[2] = 330;
      mScratch128i[3] = 333;
      mScratch128i[4] = 336;
      mScratch128i[5] = 339;
      mScratch128i[6] = 344;
      mScratch128i[7] = 346;
      mScratch128i[8] = 350;
      mScratch128i[9] = 352;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //132 044 {1.601000, -0.174580, 0.115258}
    //141 047 {1.601000, 0.174578, 0.115258}
    //276 092 {1.601000, 0.174578, 0.115258}
    //285 095 {1.601000, -0.174580, 0.115258}
    //288 096 {1.601000, 0.174578, 0.115258}
    //300 100 {1.601000, -0.174580, 0.115258}
    //996 332 {1.601000, 0.174578, 0.115258}
    //1002 334 {1.601000, 0.174578, 0.115258}
    //1014 338 {1.601000, -0.174580, 0.115258}
    //1020 340 {1.601000, -0.174580, 0.115258}
    //1026 342 {1.601000, -0.174580, 0.115258}
    //1035 345 {1.601000, -0.174580, 0.115258}
    //1044 348 {1.601000, 0.174578, 0.115258}
    //1053 351 {1.601000, 0.174578, 0.115258}
    //1494 498 {1.601000, -0.174580, 0.115258}
    //1503 501 {1.601000, -0.174580, 0.115258}
    //1515 505 {1.601000, 0.174578, 0.115258}
    //1521 507 {1.601000, 0.174578, 0.115258}
    synchronized (mScratch128i) {
      mScratch128i[0] = 44;
      mScratch128i[1] = 47;
      mScratch128i[2] = 92;
      mScratch128i[3] = 95;
      mScratch128i[4] = 96;
      mScratch128i[5] = 100;
      mScratch128i[6] = 332;
      mScratch128i[7] = 334;
      mScratch128i[8] = 338;
      mScratch128i[9] = 340;
      mScratch128i[10] = 342;
      mScratch128i[11] = 345;
      mScratch128i[12] = 348;
      mScratch128i[13] = 351;
      mScratch128i[14] = 498;
      mScratch128i[15] = 501;
      mScratch128i[16] = 505;
      mScratch128i[17] = 507;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<18; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1005 335 {1.717931, 0.370600, -0.006018}
    //1023 341 {1.717931, -0.370602, -0.006018}
    //1041 347 {1.717931, -0.370602, -0.006018}
    //1059 353 {1.717931, 0.370600, -0.006018}
    synchronized (mScratch128i) {
      mScratch128i[0] = 335;
      mScratch128i[1] = 341;
      mScratch128i[2] = 347;
      mScratch128i[3] = 353;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //003 001 {2.347583, -0.000002, 0.636507}
    //009 003 {2.347583, -0.000002, 0.636507}
    //522 174 {2.347583, -0.000002, 0.636507}
    //534 178 {2.347583, -0.000002, 0.636507}
    //1062 354 {2.347583, -0.000002, 0.636507}
    //1083 361 {2.347583, -0.000002, 0.636507}
    //1446 482 {2.347583, -0.000002, 0.636507}
    //1452 484 {2.347583, -0.000002, 0.636507}
    //1464 488 {2.347583, -0.000002, 0.636507}
    //1473 491 {2.347583, -0.000002, 0.636507}
    synchronized (mScratch128i) {
      mScratch128i[0] = 1;
      mScratch128i[1] = 3;
      mScratch128i[2] = 174;
      mScratch128i[3] = 178;
      mScratch128i[4] = 354;
      mScratch128i[5] = 361;
      mScratch128i[6] = 482;
      mScratch128i[7] = 484;
      mScratch128i[8] = 488;
      mScratch128i[9] = 491;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1068 356 {2.482028, -0.000003, 0.724578}
    //1077 359 {2.482028, -0.000003, 0.724578}
    //1086 362 {2.482028, -0.000003, 0.724578}
    //1092 364 {2.482028, -0.000003, 0.724578}
    synchronized (mScratch128i) {
      mScratch128i[0] = 356;
      mScratch128i[1] = 359;
      mScratch128i[2] = 362;
      mScratch128i[3] = 364;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //000 000 {2.543541, -0.024770, 0.616578}
    //012 004 {2.543541, 0.024767, 0.616579}
    //294 098 {2.582732, 0.080301, 0.307799}
    //303 101 {2.582732, -0.080303, 0.307799}
    //540 180 {2.582732, 0.080301, 0.307799}
    //549 183 {2.569668, -0.000001, 0.250125}
    //552 184 {2.582732, 0.080301, 0.307799}
    //558 186 {2.582732, -0.080303, 0.307799}
    //561 187 {2.569668, -0.000001, 0.250125}
    //567 189 {2.582732, -0.080303, 0.307799}
    //858 286 {2.543541, 0.024767, 0.616579}
    //864 288 {2.543541, -0.024770, 0.616578}
    //1065 355 {2.543541, -0.024770, 0.616578}
    //1071 357 {2.543541, -0.024770, 0.616578}
    //1080 360 {2.543541, 0.024767, 0.616579}
    //1089 363 {2.543541, 0.024767, 0.616579}
    //1437 479 {2.582732, 0.080301, 0.307799}
    //1488 496 {2.582732, -0.080303, 0.307799}
    //1500 500 {2.569668, -0.000001, 0.250125}
    //1506 502 {2.569668, -0.000001, 0.250125}
    //1509 503 {2.582732, -0.080303, 0.307799}
    //1518 506 {2.569668, -0.000001, 0.250125}
    //1524 508 {2.582732, 0.080301, 0.307799}
    //1527 509 {2.569668, -0.000001, 0.250125}
    synchronized (mScratch128i) {
      mScratch128i[0] = 0;
      mScratch128i[1] = 4;
      mScratch128i[2] = 98;
      mScratch128i[3] = 101;
      mScratch128i[4] = 180;
      mScratch128i[5] = 183;
      mScratch128i[6] = 184;
      mScratch128i[7] = 186;
      mScratch128i[8] = 187;
      mScratch128i[9] = 189;
      mScratch128i[10] = 286;
      mScratch128i[11] = 288;
      mScratch128i[12] = 355;
      mScratch128i[13] = 357;
      mScratch128i[14] = 360;
      mScratch128i[15] = 363;
      mScratch128i[16] = 479;
      mScratch128i[17] = 496;
      mScratch128i[18] = 500;
      mScratch128i[19] = 502;
      mScratch128i[20] = 503;
      mScratch128i[21] = 506;
      mScratch128i[22] = 508;
      mScratch128i[23] = 509;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<24; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //312 104 {2.657598, -0.100690, 0.468107}
    //321 107 {2.657598, 0.100688, 0.468108}
    //525 175 {2.657598, -0.100690, 0.468107}
    //531 177 {2.657598, 0.100688, 0.468108}
    //543 181 {2.657598, 0.100688, 0.468108}
    //573 191 {2.657598, -0.100690, 0.468107}
    //579 193 {2.657598, -0.100690, 0.468107}
    //585 195 {2.657598, 0.100688, 0.468108}
    //597 199 {2.657598, 0.100688, 0.468108}
    //621 207 {2.657598, -0.100690, 0.468107}
    //861 287 {2.622674, -0.000003, 0.750967}
    //870 290 {2.622674, -0.000003, 0.750967}
    //1074 358 {2.622674, -0.000003, 0.750967}
    //1095 365 {2.622674, -0.000003, 0.750967}
    //1296 432 {2.657598, 0.100688, 0.468108}
    //1317 439 {2.657598, -0.100690, 0.468107}
    //1428 476 {2.657598, 0.100688, 0.468108}
    //1434 478 {2.657598, 0.100688, 0.468108}
    //1455 485 {2.657598, 0.100688, 0.468108}
    //1470 490 {2.657598, -0.100690, 0.468107}
    //1482 494 {2.657598, -0.100690, 0.468107}
    //1491 497 {2.657598, -0.100690, 0.468107}
    synchronized (mScratch128i) {
      mScratch128i[0] = 104;
      mScratch128i[1] = 107;
      mScratch128i[2] = 175;
      mScratch128i[3] = 177;
      mScratch128i[4] = 181;
      mScratch128i[5] = 191;
      mScratch128i[6] = 193;
      mScratch128i[7] = 195;
      mScratch128i[8] = 199;
      mScratch128i[9] = 207;
      mScratch128i[10] = 287;
      mScratch128i[11] = 290;
      mScratch128i[12] = 358;
      mScratch128i[13] = 365;
      mScratch128i[14] = 432;
      mScratch128i[15] = 439;
      mScratch128i[16] = 476;
      mScratch128i[17] = 478;
      mScratch128i[18] = 485;
      mScratch128i[19] = 490;
      mScratch128i[20] = 494;
      mScratch128i[21] = 497;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<22; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //006 002 {2.752562, -0.000002, 0.574001}
    //015 005 {2.752562, -0.000002, 0.574001}
    //528 176 {2.752562, -0.000002, 0.574001}
    //537 179 {2.752562, -0.000002, 0.574001}
    //576 192 {2.752562, -0.000002, 0.574001}
    //588 196 {2.752562, -0.000002, 0.574001}
    //855 285 {2.752562, -0.000002, 0.574001}
    //867 289 {2.752562, -0.000002, 0.574001}
    synchronized (mScratch128i) {
      mScratch128i[0] = 2;
      mScratch128i[1] = 5;
      mScratch128i[2] = 176;
      mScratch128i[3] = 179;
      mScratch128i[4] = 192;
      mScratch128i[5] = 196;
      mScratch128i[6] = 285;
      mScratch128i[7] = 289;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //546 182 {3.025897, -0.000001, 0.358156}
    //555 185 {3.025897, -0.000001, 0.358156}
    //564 188 {3.025897, -0.000001, 0.358156}
    //570 190 {3.025897, -0.000001, 0.358156}
    //582 194 {3.025897, -0.000002, 0.537033}
    //591 197 {3.025897, -0.000002, 0.537033}
    //594 198 {3.025897, -0.000001, 0.358156}
    //603 201 {3.025897, -0.000001, 0.358156}
    //612 204 {3.025897, -0.000001, 0.358156}
    //624 208 {3.025897, -0.000001, 0.358156}
    //1299 433 {3.025897, -0.000002, 0.537033}
    //1305 435 {3.025897, -0.000002, 0.537033}
    //1314 438 {3.025897, -0.000002, 0.537033}
    //1323 441 {3.025897, -0.000002, 0.537033}
    synchronized (mScratch128i) {
      mScratch128i[0] = 182;
      mScratch128i[1] = 185;
      mScratch128i[2] = 188;
      mScratch128i[3] = 190;
      mScratch128i[4] = 194;
      mScratch128i[5] = 197;
      mScratch128i[6] = 198;
      mScratch128i[7] = 201;
      mScratch128i[8] = 204;
      mScratch128i[9] = 208;
      mScratch128i[10] = 433;
      mScratch128i[11] = 435;
      mScratch128i[12] = 438;
      mScratch128i[13] = 441;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<14; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //609 203 {3.234918, -0.000001, 0.308242}
    //615 205 {3.234918, -0.000001, 0.308242}
    //630 210 {3.234918, -0.000001, 0.308242}
    //642 214 {3.234918, -0.000001, 0.308242}
    //648 216 {3.234918, -0.000002, 0.590079}
    //660 220 {3.234918, -0.000002, 0.590079}
    //1308 436 {3.234918, -0.000002, 0.590079}
    //1329 443 {3.234918, -0.000002, 0.590079}
    synchronized (mScratch128i) {
      mScratch128i[0] = 203;
      mScratch128i[1] = 205;
      mScratch128i[2] = 210;
      mScratch128i[3] = 214;
      mScratch128i[4] = 216;
      mScratch128i[5] = 220;
      mScratch128i[6] = 436;
      mScratch128i[7] = 443;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //600 200 {3.463953, 0.010038, 0.487587}
    //606 202 {3.463953, 0.010038, 0.487587}
    //618 206 {3.463953, -0.010041, 0.487584}
    //627 209 {3.463953, -0.010041, 0.487584}
    //633 211 {3.463953, 0.010038, 0.487587}
    //639 213 {3.463953, -0.010041, 0.487584}
    //651 217 {3.463953, -0.010041, 0.487584}
    //657 219 {3.463953, 0.010038, 0.487587}
    //666 222 {3.463953, -0.010041, 0.487584}
    //675 225 {3.463953, 0.010038, 0.487587}
    //684 228 {3.463953, -0.010041, 0.487584}
    //696 232 {3.463953, 0.010038, 0.487587}
    //702 234 {3.463953, -0.010041, 0.487584}
    //714 238 {3.463953, -0.010041, 0.487584}
    //720 240 {3.463953, 0.010038, 0.487587}
    //732 244 {3.463953, 0.010038, 0.487587}
    //1260 420 {3.463953, -0.010041, 0.487584}
    //1281 427 {3.463953, 0.010038, 0.487587}
    //1302 434 {3.463953, 0.010038, 0.487587}
    //1311 437 {3.463953, 0.010038, 0.487587}
    //1320 440 {3.463953, -0.010041, 0.487584}
    //1326 442 {3.463953, -0.010041, 0.487584}
    synchronized (mScratch128i) {
      mScratch128i[0] = 200;
      mScratch128i[1] = 202;
      mScratch128i[2] = 206;
      mScratch128i[3] = 209;
      mScratch128i[4] = 211;
      mScratch128i[5] = 213;
      mScratch128i[6] = 217;
      mScratch128i[7] = 219;
      mScratch128i[8] = 222;
      mScratch128i[9] = 225;
      mScratch128i[10] = 228;
      mScratch128i[11] = 232;
      mScratch128i[12] = 234;
      mScratch128i[13] = 238;
      mScratch128i[14] = 240;
      mScratch128i[15] = 244;
      mScratch128i[16] = 420;
      mScratch128i[17] = 427;
      mScratch128i[18] = 434;
      mScratch128i[19] = 437;
      mScratch128i[20] = 440;
      mScratch128i[21] = 442;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<22; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //636 212 {3.516293, -0.000001, -0.242565}
    //645 215 {3.516293, -0.000001, -0.242565}
    //1263 421 {3.516293, -0.000001, -0.242565}
    //1269 423 {3.516293, -0.000001, -0.242565}
    //1278 426 {3.516293, -0.000001, -0.242565}
    //1287 429 {3.516293, -0.000001, -0.242565}
    synchronized (mScratch128i) {
      mScratch128i[0] = 212;
      mScratch128i[1] = 215;
      mScratch128i[2] = 421;
      mScratch128i[3] = 423;
      mScratch128i[4] = 426;
      mScratch128i[5] = 429;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //654 218 {3.777570, -0.000001, 0.949223}
    //663 221 {3.777570, -0.000001, 0.949223}
    //669 223 {3.765511, -0.000939, -0.123636}
    //672 224 {3.744657, -0.001163, 0.328944}
    //678 226 {3.744657, 0.001160, 0.328950}
    //681 227 {3.765511, 0.000937, -0.123633}
    //687 229 {3.744657, -0.001163, 0.328944}
    //693 231 {3.744657, 0.001160, 0.328950}
    //711 237 {3.777570, -0.000001, 0.949223}
    //723 241 {3.777570, -0.000001, 0.949223}
    //1266 422 {3.765511, -0.000939, -0.123636}
    //1272 424 {3.765511, -0.000001, -0.441195}
    //1275 425 {3.765511, -0.000939, -0.123636}
    //1284 428 {3.765511, 0.000937, -0.123633}
    //1290 430 {3.765511, 0.000937, -0.123633}
    //1293 431 {3.765511, -0.000001, -0.441195}
    synchronized (mScratch128i) {
      mScratch128i[0] = 218;
      mScratch128i[1] = 221;
      mScratch128i[2] = 223;
      mScratch128i[3] = 224;
      mScratch128i[4] = 226;
      mScratch128i[5] = 227;
      mScratch128i[6] = 229;
      mScratch128i[7] = 231;
      mScratch128i[8] = 237;
      mScratch128i[9] = 241;
      mScratch128i[10] = 422;
      mScratch128i[11] = 424;
      mScratch128i[12] = 425;
      mScratch128i[13] = 428;
      mScratch128i[14] = 430;
      mScratch128i[15] = 431;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<16; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //690 230 {4.256256, -0.001785, 0.767317}
    //699 233 {4.256256, 0.001781, 0.767321}
    //705 235 {4.256256, -0.001785, 0.767317}
    //729 243 {4.256256, 0.001781, 0.767321}
    synchronized (mScratch128i) {
      mScratch128i[0] = 230;
      mScratch128i[1] = 233;
      mScratch128i[2] = 235;
      mScratch128i[3] = 243;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //708 236 {5.630623, -0.000002, 1.484002}
    //717 239 {5.630623, -0.000002, 1.484002}
    //726 242 {5.630623, -0.000002, 1.484002}
    //735 245 {5.630623, -0.000002, 1.484002}
    synchronized (mScratch128i) {
      mScratch128i[0] = 236;
      mScratch128i[1] = 239;
      mScratch128i[2] = 242;
      mScratch128i[3] = 245;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    synchronized (mScratch128i) {
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<0; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //024 008 {-2.398834, -0.557398, 0.007942}
    //492 164 {-2.398834, -0.557398, 0.007942}
    //501 167 {-2.398834, -0.557398, 0.007942}
    //510 170 {-2.398834, -0.557398, 0.007942}
    //519 173 {-2.398834, -0.557398, 0.007942}
    //744 248 {-2.398834, -0.557398, 0.007942}
    //1227 409 {-2.398834, -0.557398, 0.007942}
    //1242 414 {-2.398834, -0.557398, 0.007942}
    synchronized (mScratch128i) {
      mScratch128i[0] = 8;
      mScratch128i[1] = 164;
      mScratch128i[2] = 167;
      mScratch128i[3] = 170;
      mScratch128i[4] = 173;
      mScratch128i[5] = 248;
      mScratch128i[6] = 409;
      mScratch128i[7] = 414;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //498 166 {-2.254127, -0.460289, 0.012815}
    //504 168 {-2.254127, -0.460289, 0.012815}
    //1338 446 {-2.254127, -0.460289, 0.012815}
    //1347 449 {-2.254127, -0.460289, 0.012815}
    //1356 452 {-2.254127, -0.460289, 0.012815}
    //1362 454 {-2.254127, -0.460289, 0.012815}
    synchronized (mScratch128i) {
      mScratch128i[0] = 166;
      mScratch128i[1] = 168;
      mScratch128i[2] = 446;
      mScratch128i[3] = 449;
      mScratch128i[4] = 452;
      mScratch128i[5] = 454;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //018 006 {-2.149617, -0.566497, 0.213486}
    //021 007 {-2.188389, -0.536928, 0.608049}
    //030 010 {-2.149617, -0.566498, -0.187855}
    //033 011 {-2.188389, -0.536928, -0.575509}
    //036 012 {-2.149617, -0.566497, 0.213486}
    //042 014 {-2.188389, -0.536928, 0.608049}
    //051 017 {-2.149617, -0.566498, -0.187855}
    //057 019 {-2.149617, -0.566498, -0.187855}
    //063 021 {-2.188389, -0.536928, 0.608049}
    //066 022 {-2.149617, -0.566497, 0.213486}
    //072 024 {-2.149617, -0.566498, -0.187855}
    //081 027 {-2.149617, -0.566497, 0.213486}
    //090 030 {-2.149617, -0.566497, 0.213486}
    //147 049 {-2.149617, -0.566498, -0.187855}
    //168 056 {-2.149617, -0.566497, 0.213486}
    //177 059 {-2.149617, -0.566497, 0.213486}
    //420 140 {-2.149617, -0.627089, 0.012815}
    //429 143 {-2.149617, -0.627089, 0.012815}
    //435 145 {-2.149617, -0.566497, 0.213486}
    //438 146 {-2.149617, -0.627089, 0.012815}
    //462 154 {-2.149617, -0.627089, 0.012815}
    //465 155 {-2.149617, -0.566498, -0.187855}
    //474 158 {-2.188389, -0.536928, 0.608049}
    //483 161 {-2.188389, -0.536928, -0.575509}
    //486 162 {-2.149617, -0.627089, 0.012815}
    //489 163 {-2.149617, -0.566497, 0.213486}
    //495 165 {-2.188389, -0.536928, 0.608049}
    //507 169 {-2.188389, -0.536928, -0.575509}
    //513 171 {-2.149617, -0.566498, -0.187855}
    //516 172 {-2.149617, -0.627089, 0.012815}
    //738 246 {-2.188389, -0.536928, -0.575509}
    //741 247 {-2.149617, -0.566498, -0.187855}
    //747 249 {-2.149617, -0.566498, -0.187855}
    //750 250 {-2.188389, -0.536928, -0.575509}
    //756 252 {-2.149617, -0.566498, -0.187855}
    //768 256 {-2.188389, -0.536928, -0.575509}
    //774 258 {-2.188389, -0.536928, 0.608049}
    //783 261 {-2.149617, -0.566497, 0.213486}
    //1173 391 {-2.149617, -0.566497, 0.213486}
    //1191 397 {-2.149617, -0.566497, 0.213486}
    //1206 402 {-2.149617, -0.566498, -0.187855}
    //1224 408 {-2.149617, -0.566497, 0.213486}
    //1230 410 {-2.188389, -0.536928, 0.608049}
    //1233 411 {-2.149617, -0.566497, 0.213486}
    //1236 412 {-2.188389, -0.536928, 0.608049}
    //1245 415 {-2.149617, -0.566498, -0.187855}
    //1248 416 {-2.188389, -0.536928, -0.575509}
    //1251 417 {-2.149617, -0.566498, -0.187855}
    //1257 419 {-2.188389, -0.536928, -0.575509}
    //1344 448 {-2.188389, -0.536928, -0.575509}
    //1365 455 {-2.188389, -0.536928, 0.608049}
    synchronized (mScratch128i) {
      mScratch128i[0] = 6;
      mScratch128i[1] = 7;
      mScratch128i[2] = 10;
      mScratch128i[3] = 11;
      mScratch128i[4] = 12;
      mScratch128i[5] = 14;
      mScratch128i[6] = 17;
      mScratch128i[7] = 19;
      mScratch128i[8] = 21;
      mScratch128i[9] = 22;
      mScratch128i[10] = 24;
      mScratch128i[11] = 27;
      mScratch128i[12] = 30;
      mScratch128i[13] = 49;
      mScratch128i[14] = 56;
      mScratch128i[15] = 59;
      mScratch128i[16] = 140;
      mScratch128i[17] = 143;
      mScratch128i[18] = 145;
      mScratch128i[19] = 146;
      mScratch128i[20] = 154;
      mScratch128i[21] = 155;
      mScratch128i[22] = 158;
      mScratch128i[23] = 161;
      mScratch128i[24] = 162;
      mScratch128i[25] = 163;
      mScratch128i[26] = 165;
      mScratch128i[27] = 169;
      mScratch128i[28] = 171;
      mScratch128i[29] = 172;
      mScratch128i[30] = 246;
      mScratch128i[31] = 247;
      mScratch128i[32] = 249;
      mScratch128i[33] = 250;
      mScratch128i[34] = 252;
      mScratch128i[35] = 256;
      mScratch128i[36] = 258;
      mScratch128i[37] = 261;
      mScratch128i[38] = 391;
      mScratch128i[39] = 397;
      mScratch128i[40] = 402;
      mScratch128i[41] = 408;
      mScratch128i[42] = 410;
      mScratch128i[43] = 411;
      mScratch128i[44] = 412;
      mScratch128i[45] = 415;
      mScratch128i[46] = 416;
      mScratch128i[47] = 417;
      mScratch128i[48] = 419;
      mScratch128i[49] = 448;
      mScratch128i[50] = 455;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<51; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //069 023 {-2.057164, -0.565598, 1.012195}
    //075 025 {-2.057164, -0.565599, -0.972742}
    //084 028 {-2.057164, -0.565598, 1.012195}
    //096 032 {-2.057164, -0.565598, 1.012195}
    //753 251 {-2.057164, -0.565599, -0.972742}
    //762 254 {-2.057164, -0.565599, -0.972742}
    //771 257 {-2.057164, -0.565599, -0.972742}
    //780 260 {-2.057164, -0.565598, 1.012195}
    //786 262 {-2.057164, -0.565598, 1.012195}
    //873 291 {-2.057164, -0.565599, -0.972742}
    //891 297 {-2.057164, -0.565598, 1.012195}
    //1239 413 {-2.057164, -0.565598, 1.012195}
    //1254 418 {-2.057164, -0.565599, -0.972742}
    synchronized (mScratch128i) {
      mScratch128i[0] = 23;
      mScratch128i[1] = 25;
      mScratch128i[2] = 28;
      mScratch128i[3] = 32;
      mScratch128i[4] = 251;
      mScratch128i[5] = 254;
      mScratch128i[6] = 257;
      mScratch128i[7] = 260;
      mScratch128i[8] = 262;
      mScratch128i[9] = 291;
      mScratch128i[10] = 297;
      mScratch128i[11] = 413;
      mScratch128i[12] = 418;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<13; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //447 149 {-1.979976, -0.433101, 0.538349}
    //453 151 {-1.979976, -0.433101, -0.512718}
    //471 157 {-1.979976, -0.433101, 0.538349}
    //477 159 {-1.979976, -0.433101, -0.512718}
    //1335 445 {-1.979976, -0.433101, -0.512718}
    //1341 447 {-1.979976, -0.433101, -0.512718}
    //1350 450 {-1.979976, -0.433101, 0.538349}
    //1359 453 {-1.979976, -0.433101, 0.538349}
    //1374 458 {-1.979976, -0.433101, -0.512718}
    //1383 461 {-1.979976, -0.433101, -0.512718}
    //1392 464 {-1.979976, -0.433101, 0.538349}
    //1398 466 {-1.979976, -0.433101, 0.538349}
    synchronized (mScratch128i) {
      mScratch128i[0] = 149;
      mScratch128i[1] = 151;
      mScratch128i[2] = 157;
      mScratch128i[3] = 159;
      mScratch128i[4] = 445;
      mScratch128i[5] = 447;
      mScratch128i[6] = 450;
      mScratch128i[7] = 453;
      mScratch128i[8] = 458;
      mScratch128i[9] = 461;
      mScratch128i[10] = 464;
      mScratch128i[11] = 466;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<12; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //027 009 {-1.848046, -0.539574, -0.939964}
    //039 013 {-1.850482, -0.539977, 0.977750}
    //060 020 {-1.848046, -0.539574, -0.939964}
    //078 026 {-1.848046, -0.539574, -0.939964}
    //087 029 {-1.850482, -0.539977, 0.977750}
    //093 031 {-1.848143, -0.547305, 0.977214}
    //150 050 {-1.848046, -0.539574, -0.939964}
    //156 052 {-1.848046, -0.539574, -0.939964}
    //174 058 {-1.850482, -0.539977, 0.977750}
    //468 156 {-1.850482, -0.539977, 0.977750}
    //480 160 {-1.848046, -0.539574, -0.939964}
    //759 253 {-1.848143, -0.602786, -0.882203}
    //765 255 {-1.848046, -0.539574, -0.939964}
    //777 259 {-1.850482, -0.539977, 0.977750}
    //789 263 {-1.848143, -0.602785, 0.921660}
    //804 268 {-1.848046, -0.539574, -0.939964}
    //819 273 {-1.850482, -0.539977, 0.977750}
    //876 292 {-1.848143, -0.602786, -0.882203}
    //879 293 {-1.848046, -0.539574, -0.939964}
    //885 295 {-1.848046, -0.539574, -0.939964}
    //888 296 {-1.848143, -0.602786, -0.882203}
    //894 298 {-1.848143, -0.547305, 0.977214}
    //897 299 {-1.848143, -0.602785, 0.921660}
    //903 301 {-1.848143, -0.602785, 0.921660}
    //906 302 {-1.850482, -0.539977, 0.977750}
    //1176 392 {-1.848143, -0.602785, 0.921660}
    //1182 394 {-1.848143, -0.602785, 0.921660}
    //1194 398 {-1.850482, -0.539977, 0.977750}
    //1200 400 {-1.850482, -0.539977, 0.977750}
    //1212 404 {-1.848143, -0.602786, -0.882203}
    //1221 407 {-1.848143, -0.602786, -0.882203}
    //1380 460 {-1.848046, -0.539574, -0.939964}
    //1401 467 {-1.850482, -0.539977, 0.977750}
    synchronized (mScratch128i) {
      mScratch128i[0] = 9;
      mScratch128i[1] = 13;
      mScratch128i[2] = 20;
      mScratch128i[3] = 26;
      mScratch128i[4] = 29;
      mScratch128i[5] = 31;
      mScratch128i[6] = 50;
      mScratch128i[7] = 52;
      mScratch128i[8] = 58;
      mScratch128i[9] = 156;
      mScratch128i[10] = 160;
      mScratch128i[11] = 253;
      mScratch128i[12] = 255;
      mScratch128i[13] = 259;
      mScratch128i[14] = 263;
      mScratch128i[15] = 268;
      mScratch128i[16] = 273;
      mScratch128i[17] = 292;
      mScratch128i[18] = 293;
      mScratch128i[19] = 295;
      mScratch128i[20] = 296;
      mScratch128i[21] = 298;
      mScratch128i[22] = 299;
      mScratch128i[23] = 301;
      mScratch128i[24] = 302;
      mScratch128i[25] = 392;
      mScratch128i[26] = 394;
      mScratch128i[27] = 398;
      mScratch128i[28] = 400;
      mScratch128i[29] = 404;
      mScratch128i[30] = 407;
      mScratch128i[31] = 460;
      mScratch128i[32] = 467;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<33; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //045 015 {-1.691378, -0.511986, -0.319164}
    //048 016 {-1.691378, -0.592644, -0.282309}
    //054 018 {-1.691378, -0.511986, -0.319164}
    //144 048 {-1.691378, -0.511986, -0.319164}
    //153 051 {-1.691378, -0.511986, -0.319164}
    //159 053 {-1.615305, -0.519135, -0.852975}
    //162 054 {-1.691378, -0.528765, 0.339597}
    //165 055 {-1.691378, -0.458407, 0.349528}
    //171 057 {-1.691378, -0.458407, 0.349528}
    //225 075 {-1.679747, -0.210865, -0.008253}
    //237 079 {-1.679747, -0.210865, -0.008253}
    //330 110 {-1.691378, -0.592644, -0.282309}
    //339 113 {-1.691378, -0.528765, 0.339597}
    //348 116 {-1.691378, -0.528765, 0.339597}
    //354 118 {-1.691378, -0.458407, 0.349528}
    //357 119 {-1.691378, -0.528765, 0.339597}
    //363 121 {-1.691378, -0.321478, 0.287137}
    //366 122 {-1.691378, -0.458407, 0.349528}
    //375 125 {-1.691378, -0.321478, 0.287137}
    //381 127 {-1.679747, -0.210865, -0.008253}
    //384 128 {-1.691378, -0.321478, 0.287137}
    //387 129 {-1.679747, -0.210865, -0.008253}
    //393 131 {-1.691378, -0.321479, -0.261506}
    //402 134 {-1.691378, -0.321479, -0.261506}
    //408 136 {-1.691378, -0.511986, -0.319164}
    //411 137 {-1.691378, -0.321479, -0.261506}
    //414 138 {-1.691378, -0.592644, -0.282309}
    //426 142 {-1.691378, -0.528765, 0.339597}
    //432 144 {-1.691378, -0.528765, 0.339597}
    //441 147 {-1.691378, -0.321478, 0.287137}
    //444 148 {-1.679747, -0.210865, -0.008253}
    //450 150 {-1.691378, -0.321479, -0.261506}
    //456 152 {-1.679747, -0.210865, -0.008253}
    //459 153 {-1.691378, -0.592644, -0.282309}
    //792 264 {-1.691378, -0.592644, -0.282309}
    //795 265 {-1.691378, -0.511986, -0.319164}
    //798 266 {-1.615305, -0.519135, -0.852975}
    //801 267 {-1.691378, -0.511986, -0.319164}
    //807 269 {-1.615305, -0.519135, -0.852975}
    //810 270 {-1.691378, -0.458407, 0.349528}
    //813 271 {-1.691378, -0.528765, 0.339597}
    //816 272 {-1.615305, -0.519135, 0.892432}
    //822 274 {-1.691378, -0.458407, 0.349528}
    //825 275 {-1.615305, -0.519135, 0.892432}
    //882 294 {-1.615305, -0.519135, -0.852975}
    //900 300 {-1.615305, -0.519135, 0.892432}
    //1170 390 {-1.691378, -0.528765, 0.339597}
    //1179 393 {-1.691378, -0.528765, 0.339597}
    //1185 395 {-1.615305, -0.519135, 0.892432}
    //1188 396 {-1.691378, -0.528765, 0.339597}
    //1197 399 {-1.691378, -0.528765, 0.339597}
    //1203 401 {-1.615305, -0.519135, 0.892432}
    //1209 403 {-1.691378, -0.592644, -0.282309}
    //1215 405 {-1.691378, -0.592644, -0.282309}
    //1218 406 {-1.615305, -0.519135, -0.852975}
    //1332 444 {-1.679747, -0.210865, -0.008253}
    //1353 451 {-1.679747, -0.210865, -0.008253}
    //1368 456 {-1.691378, -0.321479, -0.261506}
    //1371 457 {-1.691378, -0.511986, -0.319164}
    //1377 459 {-1.691378, -0.511986, -0.319164}
    //1386 462 {-1.691378, -0.458407, 0.349528}
    //1389 463 {-1.691378, -0.321478, 0.287137}
    //1395 465 {-1.691378, -0.458407, 0.349528}
    //1410 470 {-1.691378, -0.511986, -0.319164}
    //1416 472 {-1.691378, -0.592644, -0.282309}
    //1419 473 {-1.691378, -0.511986, -0.319164}
    synchronized (mScratch128i) {
      mScratch128i[0] = 15;
      mScratch128i[1] = 16;
      mScratch128i[2] = 18;
      mScratch128i[3] = 48;
      mScratch128i[4] = 51;
      mScratch128i[5] = 53;
      mScratch128i[6] = 54;
      mScratch128i[7] = 55;
      mScratch128i[8] = 57;
      mScratch128i[9] = 75;
      mScratch128i[10] = 79;
      mScratch128i[11] = 110;
      mScratch128i[12] = 113;
      mScratch128i[13] = 116;
      mScratch128i[14] = 118;
      mScratch128i[15] = 119;
      mScratch128i[16] = 121;
      mScratch128i[17] = 122;
      mScratch128i[18] = 125;
      mScratch128i[19] = 127;
      mScratch128i[20] = 128;
      mScratch128i[21] = 129;
      mScratch128i[22] = 131;
      mScratch128i[23] = 134;
      mScratch128i[24] = 136;
      mScratch128i[25] = 137;
      mScratch128i[26] = 138;
      mScratch128i[27] = 142;
      mScratch128i[28] = 144;
      mScratch128i[29] = 147;
      mScratch128i[30] = 148;
      mScratch128i[31] = 150;
      mScratch128i[32] = 152;
      mScratch128i[33] = 153;
      mScratch128i[34] = 264;
      mScratch128i[35] = 265;
      mScratch128i[36] = 266;
      mScratch128i[37] = 267;
      mScratch128i[38] = 269;
      mScratch128i[39] = 270;
      mScratch128i[40] = 271;
      mScratch128i[41] = 272;
      mScratch128i[42] = 274;
      mScratch128i[43] = 275;
      mScratch128i[44] = 294;
      mScratch128i[45] = 300;
      mScratch128i[46] = 390;
      mScratch128i[47] = 393;
      mScratch128i[48] = 395;
      mScratch128i[49] = 396;
      mScratch128i[50] = 399;
      mScratch128i[51] = 401;
      mScratch128i[52] = 403;
      mScratch128i[53] = 405;
      mScratch128i[54] = 406;
      mScratch128i[55] = 444;
      mScratch128i[56] = 451;
      mScratch128i[57] = 456;
      mScratch128i[58] = 457;
      mScratch128i[59] = 459;
      mScratch128i[60] = 462;
      mScratch128i[61] = 463;
      mScratch128i[62] = 465;
      mScratch128i[63] = 470;
      mScratch128i[64] = 472;
      mScratch128i[65] = 473;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<66; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //099 033 {-1.030350, -0.484149, 0.329853}
    //111 037 {-1.030349, -0.484149, -0.304222}
    //180 060 {-1.030350, -0.484149, 0.329853}
    //192 064 {-1.030349, -0.484149, -0.304222}
    //201 067 {-1.030349, -0.484149, -0.304222}
    //207 069 {-1.030350, -0.484149, 0.329853}
    //324 108 {-1.030349, -0.484149, -0.304222}
    //336 112 {-1.030350, -0.484149, 0.329853}
    //342 114 {-1.030350, -0.484149, 0.329853}
    //828 276 {-1.030350, -0.484149, 0.329853}
    //945 315 {-1.030349, -0.484149, -0.304222}
    //1137 379 {-1.030350, -0.484149, 0.329853}
    //1152 384 {-1.030349, -0.484149, -0.304222}
    //1407 469 {-1.030349, -0.484149, -0.304222}
    //1413 471 {-1.030349, -0.484149, -0.304222}
    synchronized (mScratch128i) {
      mScratch128i[0] = 33;
      mScratch128i[1] = 37;
      mScratch128i[2] = 60;
      mScratch128i[3] = 64;
      mScratch128i[4] = 67;
      mScratch128i[5] = 69;
      mScratch128i[6] = 108;
      mScratch128i[7] = 112;
      mScratch128i[8] = 114;
      mScratch128i[9] = 276;
      mScratch128i[10] = 315;
      mScratch128i[11] = 379;
      mScratch128i[12] = 384;
      mScratch128i[13] = 469;
      mScratch128i[14] = 471;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<15; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //228 076 {-0.955447, -0.030769, 0.300949}
    //234 078 {-0.955447, -0.030768, -0.275317}
    //372 124 {-0.955447, -0.030769, 0.300949}
    //378 126 {-0.955447, -0.030769, 0.300949}
    //390 130 {-0.955447, -0.030768, -0.275317}
    //396 132 {-0.955447, -0.030768, -0.275317}
    //1695 565 {-0.955447, -0.030768, -0.275317}
    //1701 567 {-0.955447, -0.030768, -0.275317}
    //1710 570 {-0.955447, -0.030768, -0.275317}
    //1731 577 {-0.955447, -0.030769, 0.300949}
    //1746 582 {-0.955447, -0.030769, 0.300949}
    //1755 585 {-0.955447, -0.030769, 0.300949}
    synchronized (mScratch128i) {
      mScratch128i[0] = 76;
      mScratch128i[1] = 78;
      mScratch128i[2] = 124;
      mScratch128i[3] = 126;
      mScratch128i[4] = 130;
      mScratch128i[5] = 132;
      mScratch128i[6] = 565;
      mScratch128i[7] = 567;
      mScratch128i[8] = 570;
      mScratch128i[9] = 577;
      mScratch128i[10] = 582;
      mScratch128i[11] = 585;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<12; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //186 062 {-0.834075, -0.247141, 0.423287}
    //195 065 {-0.834075, -0.247140, -0.397656}
    //216 072 {-0.834075, -0.247141, 0.423287}
    //246 082 {-0.834075, -0.247140, -0.397656}
    //345 115 {-0.834075, -0.247141, 0.423287}
    //351 117 {-0.834075, -0.247141, 0.423287}
    //360 120 {-0.834075, -0.247141, 0.423287}
    //369 123 {-0.834075, -0.247141, 0.423287}
    //399 133 {-0.834075, -0.247140, -0.397656}
    //405 135 {-0.834075, -0.247140, -0.397656}
    //1404 468 {-0.834075, -0.247140, -0.397656}
    //1677 559 {-0.834075, -0.247140, -0.397656}
    //1692 564 {-0.834075, -0.247140, -0.397656}
    //1749 583 {-0.834075, -0.247141, 0.423287}
    //1764 588 {-0.834075, -0.247141, 0.423287}
    synchronized (mScratch128i) {
      mScratch128i[0] = 62;
      mScratch128i[1] = 65;
      mScratch128i[2] = 72;
      mScratch128i[3] = 82;
      mScratch128i[4] = 115;
      mScratch128i[5] = 117;
      mScratch128i[6] = 120;
      mScratch128i[7] = 123;
      mScratch128i[8] = 133;
      mScratch128i[9] = 135;
      mScratch128i[10] = 468;
      mScratch128i[11] = 559;
      mScratch128i[12] = 564;
      mScratch128i[13] = 583;
      mScratch128i[14] = 588;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<15; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //198 066 {-0.745866, -0.566090, 0.012816}
    //210 070 {-0.745866, -0.566090, 0.012816}
    //327 109 {-0.745866, -0.566090, 0.012816}
    //333 111 {-0.745866, -0.566090, 0.012816}
    //417 139 {-0.745866, -0.566090, 0.012816}
    //423 141 {-0.745866, -0.566090, 0.012816}
    //1785 595 {-0.745866, -0.566090, 0.012816}
    //1800 600 {-0.745866, -0.566090, 0.012816}
    synchronized (mScratch128i) {
      mScratch128i[0] = 66;
      mScratch128i[1] = 70;
      mScratch128i[2] = 109;
      mScratch128i[3] = 111;
      mScratch128i[4] = 139;
      mScratch128i[5] = 141;
      mScratch128i[6] = 595;
      mScratch128i[7] = 600;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //102 034 {-0.672170, -0.338415, 0.414681}
    //105 035 {-0.672170, -0.338415, 0.414681}
    //108 036 {-0.672170, -0.338413, -0.389052}
    //114 038 {-0.672170, -0.338413, -0.389052}
    //120 040 {-0.672170, -0.338413, -0.389052}
    //123 041 {-0.672170, -0.338413, -0.389052}
    //183 061 {-0.672170, -0.338415, 0.414681}
    //189 063 {-0.672170, -0.338413, -0.389052}
    //219 073 {-0.672170, -0.338415, 0.414681}
    //243 081 {-0.672170, -0.338413, -0.389052}
    //831 277 {-0.672170, -0.338415, 0.414681}
    //951 317 {-0.672170, -0.338413, -0.389052}
    //969 323 {-0.672170, -0.338415, 0.414681}
    //975 325 {-0.672170, -0.338413, -0.389052}
    synchronized (mScratch128i) {
      mScratch128i[0] = 34;
      mScratch128i[1] = 35;
      mScratch128i[2] = 36;
      mScratch128i[3] = 38;
      mScratch128i[4] = 40;
      mScratch128i[5] = 41;
      mScratch128i[6] = 61;
      mScratch128i[7] = 63;
      mScratch128i[8] = 73;
      mScratch128i[9] = 81;
      mScratch128i[10] = 277;
      mScratch128i[11] = 317;
      mScratch128i[12] = 323;
      mScratch128i[13] = 325;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<14; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //117 039 {-0.419498, -0.303402, -0.328394}
    //204 068 {-0.419498, -0.303402, -0.328394}
    //213 071 {-0.419499, -0.303404, 0.354023}
    //222 074 {-0.419499, -0.303404, 0.354023}
    //249 083 {-0.419498, -0.303402, -0.328394}
    //834 278 {-0.401885, -0.395587, 0.913889}
    //948 316 {-0.401885, -0.395587, -0.906539}
    //954 318 {-0.419499, -0.303404, 0.354023}
    //960 320 {-0.401885, -0.395587, 0.913889}
    //963 321 {-0.419499, -0.303404, 0.354023}
    //966 322 {-0.401885, -0.395587, 0.913889}
    //972 324 {-0.419498, -0.303402, -0.328394}
    //978 326 {-0.401885, -0.395587, -0.906539}
    //981 327 {-0.419498, -0.303402, -0.328394}
    //984 328 {-0.401885, -0.395587, -0.906539}
    //1134 378 {-0.419499, -0.303404, 0.354023}
    //1140 380 {-0.401885, -0.395587, 0.913889}
    //1143 381 {-0.419499, -0.303404, 0.354023}
    //1146 382 {-0.401885, -0.395587, 0.913889}
    //1155 385 {-0.419498, -0.303402, -0.328394}
    //1158 386 {-0.401885, -0.395587, -0.906539}
    //1161 387 {-0.419498, -0.303402, -0.328394}
    //1167 389 {-0.401885, -0.395587, -0.906539}
    //1674 558 {-0.419498, -0.303402, -0.328394}
    //1683 561 {-0.419498, -0.303402, -0.328394}
    //1767 589 {-0.419499, -0.303404, 0.354023}
    //1773 591 {-0.419499, -0.303404, 0.354023}
    //1782 594 {-0.419499, -0.303404, 0.354023}
    //1791 597 {-0.419499, -0.303404, 0.354023}
    //1803 601 {-0.419498, -0.303402, -0.328394}
    //1809 603 {-0.419498, -0.303402, -0.328394}
    synchronized (mScratch128i) {
      mScratch128i[0] = 39;
      mScratch128i[1] = 68;
      mScratch128i[2] = 71;
      mScratch128i[3] = 74;
      mScratch128i[4] = 83;
      mScratch128i[5] = 278;
      mScratch128i[6] = 316;
      mScratch128i[7] = 318;
      mScratch128i[8] = 320;
      mScratch128i[9] = 321;
      mScratch128i[10] = 322;
      mScratch128i[11] = 324;
      mScratch128i[12] = 326;
      mScratch128i[13] = 327;
      mScratch128i[14] = 328;
      mScratch128i[15] = 378;
      mScratch128i[16] = 380;
      mScratch128i[17] = 381;
      mScratch128i[18] = 382;
      mScratch128i[19] = 385;
      mScratch128i[20] = 386;
      mScratch128i[21] = 387;
      mScratch128i[22] = 389;
      mScratch128i[23] = 558;
      mScratch128i[24] = 561;
      mScratch128i[25] = 589;
      mScratch128i[26] = 591;
      mScratch128i[27] = 594;
      mScratch128i[28] = 597;
      mScratch128i[29] = 601;
      mScratch128i[30] = 603;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<31; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //231 077 {-0.380746, 0.298976, 0.007275}
    //240 080 {-0.380746, 0.298976, 0.007275}
    //840 280 {-0.380746, 0.298976, 0.007275}
    //846 282 {-0.380746, 0.298976, 0.007275}
    //1713 571 {-0.380746, 0.298976, 0.007275}
    //1719 573 {-0.380746, 0.298976, 0.007275}
    //1728 576 {-0.380746, 0.298976, 0.007275}
    //1737 579 {-0.380746, 0.298976, 0.007275}
    synchronized (mScratch128i) {
      mScratch128i[0] = 77;
      mScratch128i[1] = 80;
      mScratch128i[2] = 280;
      mScratch128i[3] = 282;
      mScratch128i[4] = 571;
      mScratch128i[5] = 573;
      mScratch128i[6] = 576;
      mScratch128i[7] = 579;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //252 084 {0.043326, 0.427529, 0.122775}
    //849 283 {0.043326, 0.427529, 0.122775}
    //957 319 {0.001005, -0.385558, 1.123781}
    //987 329 {0.001006, -0.385558, -1.116434}
    //1098 366 {0.043326, 0.427529, 0.122775}
    //1149 383 {0.001005, -0.385558, 1.123781}
    //1164 388 {0.001006, -0.385558, -1.116434}
    //1743 581 {0.043326, 0.427529, 0.122775}
    synchronized (mScratch128i) {
      mScratch128i[0] = 84;
      mScratch128i[1] = 283;
      mScratch128i[2] = 319;
      mScratch128i[3] = 329;
      mScratch128i[4] = 366;
      mScratch128i[5] = 383;
      mScratch128i[6] = 388;
      mScratch128i[7] = 581;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1110 370 {0.243681, 1.577932, 0.012821}
    //1131 377 {0.243681, 1.577932, 0.012821}
    synchronized (mScratch128i) {
      mScratch128i[0] = 370;
      mScratch128i[1] = 377;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<2; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //255 085 {0.372936, 0.407830, 0.320376}
    //261 087 {0.372936, 0.407832, -0.294743}
    //1530 510 {0.372936, -0.133725, -0.348838}
    //1533 511 {0.372936, 0.104152, -0.415852}
    //1539 513 {0.372936, -0.133725, -0.348838}
    //1548 516 {0.372936, 0.104152, -0.415852}
    //1551 517 {0.372936, 0.407832, -0.294743}
    //1557 519 {0.372936, 0.407832, -0.294743}
    //1566 522 {0.372936, 0.407832, -0.294743}
    //1587 529 {0.372936, 0.407830, 0.320376}
    //1602 534 {0.372936, 0.407830, 0.320376}
    //1605 535 {0.372936, 0.104149, 0.441483}
    //1611 537 {0.372936, 0.407830, 0.320376}
    //1620 540 {0.372936, 0.104149, 0.441483}
    //1623 541 {0.372936, -0.133727, 0.374468}
    //1629 543 {0.372936, -0.133727, 0.374468}
    //1638 546 {0.372936, -0.133727, 0.374468}
    //1641 547 {0.372936, -0.305095, 0.012816}
    //1647 549 {0.372936, -0.305095, 0.012816}
    //1656 552 {0.372936, -0.305095, 0.012816}
    //1659 553 {0.372936, -0.133725, -0.348838}
    //1665 555 {0.372936, -0.305095, 0.012816}
    //1680 560 {0.372936, 0.104152, -0.415852}
    //1686 562 {0.372936, 0.104152, -0.415852}
    //1689 563 {0.372936, -0.133725, -0.348838}
    //1698 566 {0.372936, 0.104152, -0.415852}
    //1704 568 {0.372936, 0.407832, -0.294743}
    //1707 569 {0.372936, 0.104152, -0.415852}
    //1716 572 {0.372936, 0.407832, -0.294743}
    //1725 575 {0.372936, 0.407832, -0.294743}
    //1734 578 {0.372936, 0.407830, 0.320376}
    //1740 580 {0.372936, 0.407830, 0.320376}
    //1752 584 {0.372936, 0.104149, 0.441483}
    //1758 586 {0.372936, 0.104149, 0.441483}
    //1761 587 {0.372936, 0.407830, 0.320376}
    //1770 590 {0.372936, 0.104149, 0.441483}
    //1776 592 {0.372936, -0.133727, 0.374468}
    //1779 593 {0.372936, 0.104149, 0.441483}
    //1788 596 {0.372936, -0.305095, 0.012816}
    //1794 598 {0.372936, -0.305095, 0.012816}
    //1797 599 {0.372936, -0.133727, 0.374468}
    //1806 602 {0.372936, -0.305095, 0.012816}
    //1812 604 {0.372936, -0.133725, -0.348838}
    //1815 605 {0.372936, -0.305095, 0.012816}
    synchronized (mScratch128i) {
      mScratch128i[0] = 85;
      mScratch128i[1] = 87;
      mScratch128i[2] = 510;
      mScratch128i[3] = 511;
      mScratch128i[4] = 513;
      mScratch128i[5] = 516;
      mScratch128i[6] = 517;
      mScratch128i[7] = 519;
      mScratch128i[8] = 522;
      mScratch128i[9] = 529;
      mScratch128i[10] = 534;
      mScratch128i[11] = 535;
      mScratch128i[12] = 537;
      mScratch128i[13] = 540;
      mScratch128i[14] = 541;
      mScratch128i[15] = 543;
      mScratch128i[16] = 546;
      mScratch128i[17] = 547;
      mScratch128i[18] = 549;
      mScratch128i[19] = 552;
      mScratch128i[20] = 553;
      mScratch128i[21] = 555;
      mScratch128i[22] = 560;
      mScratch128i[23] = 562;
      mScratch128i[24] = 563;
      mScratch128i[25] = 566;
      mScratch128i[26] = 568;
      mScratch128i[27] = 569;
      mScratch128i[28] = 572;
      mScratch128i[29] = 575;
      mScratch128i[30] = 578;
      mScratch128i[31] = 580;
      mScratch128i[32] = 584;
      mScratch128i[33] = 586;
      mScratch128i[34] = 587;
      mScratch128i[35] = 590;
      mScratch128i[36] = 592;
      mScratch128i[37] = 593;
      mScratch128i[38] = 596;
      mScratch128i[39] = 598;
      mScratch128i[40] = 599;
      mScratch128i[41] = 602;
      mScratch128i[42] = 604;
      mScratch128i[43] = 605;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<44; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //258 086 {0.517643, 0.550521, 0.002451}
    //267 089 {0.517643, 0.550521, 0.002451}
    //1101 367 {0.517643, 0.550521, 0.002451}
    //1107 369 {0.517643, 0.550521, 0.002451}
    //1116 372 {0.517643, 0.550521, 0.002451}
    //1125 375 {0.517643, 0.550521, 0.002451}
    //1569 523 {0.517643, 0.550521, 0.002451}
    //1575 525 {0.517643, 0.550521, 0.002451}
    //1584 528 {0.517643, 0.550521, 0.002451}
    //1593 531 {0.517643, 0.550521, 0.002451}
    synchronized (mScratch128i) {
      mScratch128i[0] = 86;
      mScratch128i[1] = 89;
      mScratch128i[2] = 367;
      mScratch128i[3] = 369;
      mScratch128i[4] = 372;
      mScratch128i[5] = 375;
      mScratch128i[6] = 523;
      mScratch128i[7] = 525;
      mScratch128i[8] = 528;
      mScratch128i[9] = 531;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //309 103 {1.211701, 0.250705, 0.339352}
    //315 105 {1.211701, 0.250708, -0.313721}
    //1425 475 {1.211701, 0.250708, -0.313721}
    //1476 492 {1.211701, 0.250705, 0.339352}
    //1536 512 {1.211701, 0.250708, -0.313721}
    //1542 514 {1.211701, 0.250708, -0.313721}
    //1554 518 {1.211701, 0.250708, -0.313721}
    //1563 521 {1.211701, 0.250708, -0.313721}
    //1608 536 {1.211701, 0.250705, 0.339352}
    //1614 538 {1.211701, 0.250705, 0.339352}
    //1626 542 {1.211701, 0.250705, 0.339352}
    //1635 545 {1.211701, 0.250705, 0.339352}
    synchronized (mScratch128i) {
      mScratch128i[0] = 103;
      mScratch128i[1] = 105;
      mScratch128i[2] = 475;
      mScratch128i[3] = 492;
      mScratch128i[4] = 512;
      mScratch128i[5] = 514;
      mScratch128i[6] = 518;
      mScratch128i[7] = 521;
      mScratch128i[8] = 536;
      mScratch128i[9] = 538;
      mScratch128i[10] = 542;
      mScratch128i[11] = 545;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<12; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //126 042 {1.334634, 0.073267, 0.260798}
    //129 043 {1.385885, 0.019892, 0.212395}
    //135 045 {1.385885, 0.019891, -0.186763}
    //138 046 {1.334634, 0.073268, -0.235167}
    //270 090 {1.389905, -0.031078, 0.012816}
    //273 091 {1.385885, 0.019891, -0.186763}
    //279 093 {1.385885, 0.019892, 0.212395}
    //282 094 {1.389905, -0.031078, 0.012816}
    //291 097 {1.334634, 0.073268, -0.235167}
    //297 099 {1.334634, 0.073267, 0.260798}
    //306 102 {1.317551, 0.493202, 0.234033}
    //318 106 {1.317551, 0.493204, -0.208400}
    //909 303 {1.385885, 0.019892, 0.212395}
    //912 304 {1.334634, 0.073267, 0.260798}
    //915 305 {1.389905, -0.031078, 0.012816}
    //918 306 {1.385885, 0.019892, 0.212395}
    //921 307 {1.334634, 0.073267, 0.260798}
    //927 309 {1.334634, 0.073268, -0.235167}
    //930 310 {1.385885, 0.019891, -0.186763}
    //933 311 {1.389905, -0.031078, 0.012816}
    //936 312 {1.385885, 0.019891, -0.186763}
    //939 313 {1.334634, 0.073268, -0.235167}
    //993 331 {1.334634, 0.073268, -0.235167}
    //1011 337 {1.334634, 0.073267, 0.260798}
    //1029 343 {1.385885, 0.019892, 0.212395}
    //1047 349 {1.385885, 0.019891, -0.186763}
    //1422 474 {1.334634, 0.073268, -0.235167}
    //1431 477 {1.334634, 0.073268, -0.235167}
    //1440 480 {1.317551, 0.493204, -0.208400}
    //1443 481 {1.317551, 0.577103, 0.012817}
    //1449 483 {1.317551, 0.493204, -0.208400}
    //1458 486 {1.317551, 0.577103, 0.012817}
    //1461 487 {1.317551, 0.493202, 0.234033}
    //1467 489 {1.317551, 0.493202, 0.234033}
    //1479 493 {1.334634, 0.073267, 0.260798}
    //1485 495 {1.334634, 0.073267, 0.260798}
    //1497 499 {1.389905, -0.031078, 0.012816}
    //1512 504 {1.389905, -0.031078, 0.012816}
    //1545 515 {1.334634, 0.073268, -0.235167}
    //1560 520 {1.317551, 0.493204, -0.208400}
    //1572 524 {1.317551, 0.493204, -0.208400}
    //1578 526 {1.317551, 0.577103, 0.012817}
    //1581 527 {1.317551, 0.493204, -0.208400}
    //1590 530 {1.317551, 0.493202, 0.234033}
    //1596 532 {1.317551, 0.493202, 0.234033}
    //1599 533 {1.317551, 0.577103, 0.012817}
    //1617 539 {1.317551, 0.493202, 0.234033}
    //1632 544 {1.334634, 0.073267, 0.260798}
    //1644 548 {1.334634, 0.073267, 0.260798}
    //1650 550 {1.389905, -0.031078, 0.012816}
    //1653 551 {1.334634, 0.073267, 0.260798}
    //1662 554 {1.334634, 0.073268, -0.235167}
    //1668 556 {1.334634, 0.073268, -0.235167}
    //1671 557 {1.389905, -0.031078, 0.012816}
    synchronized (mScratch128i) {
      mScratch128i[0] = 42;
      mScratch128i[1] = 43;
      mScratch128i[2] = 45;
      mScratch128i[3] = 46;
      mScratch128i[4] = 90;
      mScratch128i[5] = 91;
      mScratch128i[6] = 93;
      mScratch128i[7] = 94;
      mScratch128i[8] = 97;
      mScratch128i[9] = 99;
      mScratch128i[10] = 102;
      mScratch128i[11] = 106;
      mScratch128i[12] = 303;
      mScratch128i[13] = 304;
      mScratch128i[14] = 305;
      mScratch128i[15] = 306;
      mScratch128i[16] = 307;
      mScratch128i[17] = 309;
      mScratch128i[18] = 310;
      mScratch128i[19] = 311;
      mScratch128i[20] = 312;
      mScratch128i[21] = 313;
      mScratch128i[22] = 331;
      mScratch128i[23] = 337;
      mScratch128i[24] = 343;
      mScratch128i[25] = 349;
      mScratch128i[26] = 474;
      mScratch128i[27] = 477;
      mScratch128i[28] = 480;
      mScratch128i[29] = 481;
      mScratch128i[30] = 483;
      mScratch128i[31] = 486;
      mScratch128i[32] = 487;
      mScratch128i[33] = 489;
      mScratch128i[34] = 493;
      mScratch128i[35] = 495;
      mScratch128i[36] = 499;
      mScratch128i[37] = 504;
      mScratch128i[38] = 515;
      mScratch128i[39] = 520;
      mScratch128i[40] = 524;
      mScratch128i[41] = 526;
      mScratch128i[42] = 527;
      mScratch128i[43] = 530;
      mScratch128i[44] = 532;
      mScratch128i[45] = 533;
      mScratch128i[46] = 539;
      mScratch128i[47] = 544;
      mScratch128i[48] = 548;
      mScratch128i[49] = 550;
      mScratch128i[50] = 551;
      mScratch128i[51] = 554;
      mScratch128i[52] = 556;
      mScratch128i[53] = 557;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<54; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //924 308 {1.524988, -0.019852, 0.340291}
    //942 314 {1.524988, -0.019852, -0.314659}
    //990 330 {1.524988, -0.019852, -0.314659}
    //999 333 {1.524988, -0.019852, -0.314659}
    //1008 336 {1.524988, -0.019852, 0.340291}
    //1017 339 {1.524988, -0.019852, 0.340291}
    //1032 344 {1.524988, -0.019852, 0.340291}
    //1038 346 {1.524988, -0.019852, 0.340291}
    //1050 350 {1.524988, -0.019852, -0.314659}
    //1056 352 {1.524988, -0.019852, -0.314659}
    synchronized (mScratch128i) {
      mScratch128i[0] = 308;
      mScratch128i[1] = 314;
      mScratch128i[2] = 330;
      mScratch128i[3] = 333;
      mScratch128i[4] = 336;
      mScratch128i[5] = 339;
      mScratch128i[6] = 344;
      mScratch128i[7] = 346;
      mScratch128i[8] = 350;
      mScratch128i[9] = 352;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //132 044 {1.601001, 0.082225, 0.187395}
    //141 047 {1.601001, 0.082224, -0.161763}
    //276 092 {1.601001, 0.082224, -0.161763}
    //285 095 {1.601001, 0.082225, 0.187395}
    //288 096 {1.601001, 0.082224, -0.161763}
    //300 100 {1.601001, 0.082225, 0.187395}
    //996 332 {1.601001, 0.082224, -0.161763}
    //1002 334 {1.601001, 0.082224, -0.161763}
    //1014 338 {1.601001, 0.082225, 0.187395}
    //1020 340 {1.601001, 0.082225, 0.187395}
    //1026 342 {1.601001, 0.082225, 0.187395}
    //1035 345 {1.601001, 0.082225, 0.187395}
    //1044 348 {1.601001, 0.082224, -0.161763}
    //1053 351 {1.601001, 0.082224, -0.161763}
    //1494 498 {1.601001, 0.082225, 0.187395}
    //1503 501 {1.601001, 0.082225, 0.187395}
    //1515 505 {1.601001, 0.082224, -0.161763}
    //1521 507 {1.601001, 0.082224, -0.161763}
    synchronized (mScratch128i) {
      mScratch128i[0] = 44;
      mScratch128i[1] = 47;
      mScratch128i[2] = 92;
      mScratch128i[3] = 95;
      mScratch128i[4] = 96;
      mScratch128i[5] = 100;
      mScratch128i[6] = 332;
      mScratch128i[7] = 334;
      mScratch128i[8] = 338;
      mScratch128i[9] = 340;
      mScratch128i[10] = 342;
      mScratch128i[11] = 345;
      mScratch128i[12] = 348;
      mScratch128i[13] = 351;
      mScratch128i[14] = 498;
      mScratch128i[15] = 501;
      mScratch128i[16] = 505;
      mScratch128i[17] = 507;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<18; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1005 335 {1.717931, -0.039052, -0.357785}
    //1023 341 {1.717931, -0.039051, 0.383417}
    //1041 347 {1.717931, -0.039051, 0.383417}
    //1059 353 {1.717931, -0.039052, -0.357785}
    synchronized (mScratch128i) {
      mScratch128i[0] = 335;
      mScratch128i[1] = 341;
      mScratch128i[2] = 347;
      mScratch128i[3] = 353;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //003 001 {2.347583, 0.603474, 0.012817}
    //009 003 {2.347583, 0.603474, 0.012817}
    //522 174 {2.347583, 0.603474, 0.012817}
    //534 178 {2.347583, 0.603474, 0.012817}
    //1062 354 {2.347583, 0.603474, 0.012817}
    //1083 361 {2.347583, 0.603474, 0.012817}
    //1446 482 {2.347583, 0.603474, 0.012817}
    //1452 484 {2.347583, 0.603474, 0.012817}
    //1464 488 {2.347583, 0.603474, 0.012817}
    //1473 491 {2.347583, 0.603474, 0.012817}
    synchronized (mScratch128i) {
      mScratch128i[0] = 1;
      mScratch128i[1] = 3;
      mScratch128i[2] = 174;
      mScratch128i[3] = 178;
      mScratch128i[4] = 354;
      mScratch128i[5] = 361;
      mScratch128i[6] = 482;
      mScratch128i[7] = 484;
      mScratch128i[8] = 488;
      mScratch128i[9] = 491;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<10; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //1068 356 {2.482028, 0.691544, 0.012818}
    //1077 359 {2.482028, 0.691544, 0.012818}
    //1086 362 {2.482028, 0.691544, 0.012818}
    //1092 364 {2.482028, 0.691544, 0.012818}
    synchronized (mScratch128i) {
      mScratch128i[0] = 356;
      mScratch128i[1] = 359;
      mScratch128i[2] = 362;
      mScratch128i[3] = 364;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //000 000 {2.543541, 0.583545, 0.037585}
    //012 004 {2.543541, 0.583545, -0.011952}
    //294 098 {2.582732, 0.274766, -0.067486}
    //303 101 {2.582732, 0.274766, 0.093118}
    //540 180 {2.582732, 0.274766, -0.067486}
    //549 183 {2.569668, 0.217092, 0.012816}
    //552 184 {2.582732, 0.274766, -0.067486}
    //558 186 {2.582732, 0.274766, 0.093118}
    //561 187 {2.569668, 0.217092, 0.012816}
    //567 189 {2.582732, 0.274766, 0.093118}
    //858 286 {2.543541, 0.583545, -0.011952}
    //864 288 {2.543541, 0.583545, 0.037585}
    //1065 355 {2.543541, 0.583545, 0.037585}
    //1071 357 {2.543541, 0.583545, 0.037585}
    //1080 360 {2.543541, 0.583545, -0.011952}
    //1089 363 {2.543541, 0.583545, -0.011952}
    //1437 479 {2.582732, 0.274766, -0.067486}
    //1488 496 {2.582732, 0.274766, 0.093118}
    //1500 500 {2.569668, 0.217092, 0.012816}
    //1506 502 {2.569668, 0.217092, 0.012816}
    //1509 503 {2.582732, 0.274766, 0.093118}
    //1518 506 {2.569668, 0.217092, 0.012816}
    //1524 508 {2.582732, 0.274766, -0.067486}
    //1527 509 {2.569668, 0.217092, 0.012816}
    synchronized (mScratch128i) {
      mScratch128i[0] = 0;
      mScratch128i[1] = 4;
      mScratch128i[2] = 98;
      mScratch128i[3] = 101;
      mScratch128i[4] = 180;
      mScratch128i[5] = 183;
      mScratch128i[6] = 184;
      mScratch128i[7] = 186;
      mScratch128i[8] = 187;
      mScratch128i[9] = 189;
      mScratch128i[10] = 286;
      mScratch128i[11] = 288;
      mScratch128i[12] = 355;
      mScratch128i[13] = 357;
      mScratch128i[14] = 360;
      mScratch128i[15] = 363;
      mScratch128i[16] = 479;
      mScratch128i[17] = 496;
      mScratch128i[18] = 500;
      mScratch128i[19] = 502;
      mScratch128i[20] = 503;
      mScratch128i[21] = 506;
      mScratch128i[22] = 508;
      mScratch128i[23] = 509;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<24; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //312 104 {2.657598, 0.435074, 0.113505}
    //321 107 {2.657598, 0.435075, -0.087873}
    //525 175 {2.657598, 0.435074, 0.113505}
    //531 177 {2.657598, 0.435075, -0.087873}
    //543 181 {2.657598, 0.435075, -0.087873}
    //573 191 {2.657598, 0.435074, 0.113505}
    //579 193 {2.657598, 0.435074, 0.113505}
    //585 195 {2.657598, 0.435075, -0.087873}
    //597 199 {2.657598, 0.435075, -0.087873}
    //621 207 {2.657598, 0.435074, 0.113505}
    //861 287 {2.622674, 0.717934, 0.012818}
    //870 290 {2.622674, 0.717934, 0.012818}
    //1074 358 {2.622674, 0.717934, 0.012818}
    //1095 365 {2.622674, 0.717934, 0.012818}
    //1296 432 {2.657598, 0.435075, -0.087873}
    //1317 439 {2.657598, 0.435074, 0.113505}
    //1428 476 {2.657598, 0.435075, -0.087873}
    //1434 478 {2.657598, 0.435075, -0.087873}
    //1455 485 {2.657598, 0.435075, -0.087873}
    //1470 490 {2.657598, 0.435074, 0.113505}
    //1482 494 {2.657598, 0.435074, 0.113505}
    //1491 497 {2.657598, 0.435074, 0.113505}
    synchronized (mScratch128i) {
      mScratch128i[0] = 104;
      mScratch128i[1] = 107;
      mScratch128i[2] = 175;
      mScratch128i[3] = 177;
      mScratch128i[4] = 181;
      mScratch128i[5] = 191;
      mScratch128i[6] = 193;
      mScratch128i[7] = 195;
      mScratch128i[8] = 199;
      mScratch128i[9] = 207;
      mScratch128i[10] = 287;
      mScratch128i[11] = 290;
      mScratch128i[12] = 358;
      mScratch128i[13] = 365;
      mScratch128i[14] = 432;
      mScratch128i[15] = 439;
      mScratch128i[16] = 476;
      mScratch128i[17] = 478;
      mScratch128i[18] = 485;
      mScratch128i[19] = 490;
      mScratch128i[20] = 494;
      mScratch128i[21] = 497;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<22; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //006 002 {2.752562, 0.540968, 0.012817}
    //015 005 {2.752562, 0.540968, 0.012817}
    //528 176 {2.752562, 0.540968, 0.012817}
    //537 179 {2.752562, 0.540968, 0.012817}
    //576 192 {2.752562, 0.540968, 0.012817}
    //588 196 {2.752562, 0.540968, 0.012817}
    //855 285 {2.752562, 0.540968, 0.012817}
    //867 289 {2.752562, 0.540968, 0.012817}
    synchronized (mScratch128i) {
      mScratch128i[0] = 2;
      mScratch128i[1] = 5;
      mScratch128i[2] = 176;
      mScratch128i[3] = 179;
      mScratch128i[4] = 192;
      mScratch128i[5] = 196;
      mScratch128i[6] = 285;
      mScratch128i[7] = 289;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //546 182 {3.025897, 0.325122, 0.012817}
    //555 185 {3.025897, 0.325122, 0.012817}
    //564 188 {3.025897, 0.325122, 0.012817}
    //570 190 {3.025897, 0.325122, 0.012817}
    //582 194 {3.025897, 0.503999, 0.012817}
    //591 197 {3.025897, 0.503999, 0.012817}
    //594 198 {3.025897, 0.325122, 0.012817}
    //603 201 {3.025897, 0.325122, 0.012817}
    //612 204 {3.025897, 0.325122, 0.012817}
    //624 208 {3.025897, 0.325122, 0.012817}
    //1299 433 {3.025897, 0.503999, 0.012817}
    //1305 435 {3.025897, 0.503999, 0.012817}
    //1314 438 {3.025897, 0.503999, 0.012817}
    //1323 441 {3.025897, 0.503999, 0.012817}
    synchronized (mScratch128i) {
      mScratch128i[0] = 182;
      mScratch128i[1] = 185;
      mScratch128i[2] = 188;
      mScratch128i[3] = 190;
      mScratch128i[4] = 194;
      mScratch128i[5] = 197;
      mScratch128i[6] = 198;
      mScratch128i[7] = 201;
      mScratch128i[8] = 204;
      mScratch128i[9] = 208;
      mScratch128i[10] = 433;
      mScratch128i[11] = 435;
      mScratch128i[12] = 438;
      mScratch128i[13] = 441;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<14; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //609 203 {3.234919, 0.275208, 0.012817}
    //615 205 {3.234919, 0.275208, 0.012817}
    //630 210 {3.234919, 0.275208, 0.012817}
    //642 214 {3.234919, 0.275208, 0.012817}
    //648 216 {3.234919, 0.557046, 0.012817}
    //660 220 {3.234919, 0.557046, 0.012817}
    //1308 436 {3.234919, 0.557046, 0.012817}
    //1329 443 {3.234919, 0.557046, 0.012817}
    synchronized (mScratch128i) {
      mScratch128i[0] = 203;
      mScratch128i[1] = 205;
      mScratch128i[2] = 210;
      mScratch128i[3] = 214;
      mScratch128i[4] = 216;
      mScratch128i[5] = 220;
      mScratch128i[6] = 436;
      mScratch128i[7] = 443;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<8; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //600 200 {3.463953, 0.454554, 0.002777}
    //606 202 {3.463953, 0.454554, 0.002777}
    //618 206 {3.463953, 0.454551, 0.022856}
    //627 209 {3.463953, 0.454551, 0.022856}
    //633 211 {3.463953, 0.454554, 0.002777}
    //639 213 {3.463953, 0.454551, 0.022856}
    //651 217 {3.463953, 0.454551, 0.022856}
    //657 219 {3.463953, 0.454554, 0.002777}
    //666 222 {3.463953, 0.454551, 0.022856}
    //675 225 {3.463953, 0.454554, 0.002777}
    //684 228 {3.463953, 0.454551, 0.022856}
    //696 232 {3.463953, 0.454554, 0.002777}
    //702 234 {3.463953, 0.454551, 0.022856}
    //714 238 {3.463953, 0.454551, 0.022856}
    //720 240 {3.463953, 0.454554, 0.002777}
    //732 244 {3.463953, 0.454554, 0.002777}
    //1260 420 {3.463953, 0.454551, 0.022856}
    //1281 427 {3.463953, 0.454554, 0.002777}
    //1302 434 {3.463953, 0.454554, 0.002777}
    //1311 437 {3.463953, 0.454554, 0.002777}
    //1320 440 {3.463953, 0.454551, 0.022856}
    //1326 442 {3.463953, 0.454551, 0.022856}
    synchronized (mScratch128i) {
      mScratch128i[0] = 200;
      mScratch128i[1] = 202;
      mScratch128i[2] = 206;
      mScratch128i[3] = 209;
      mScratch128i[4] = 211;
      mScratch128i[5] = 213;
      mScratch128i[6] = 217;
      mScratch128i[7] = 219;
      mScratch128i[8] = 222;
      mScratch128i[9] = 225;
      mScratch128i[10] = 228;
      mScratch128i[11] = 232;
      mScratch128i[12] = 234;
      mScratch128i[13] = 238;
      mScratch128i[14] = 240;
      mScratch128i[15] = 244;
      mScratch128i[16] = 420;
      mScratch128i[17] = 427;
      mScratch128i[18] = 434;
      mScratch128i[19] = 437;
      mScratch128i[20] = 440;
      mScratch128i[21] = 442;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<22; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //636 212 {3.516293, -0.275598, 0.012817}
    //645 215 {3.516293, -0.275598, 0.012817}
    //1263 421 {3.516293, -0.275598, 0.012817}
    //1269 423 {3.516293, -0.275598, 0.012817}
    //1278 426 {3.516293, -0.275598, 0.012817}
    //1287 429 {3.516293, -0.275598, 0.012817}
    synchronized (mScratch128i) {
      mScratch128i[0] = 212;
      mScratch128i[1] = 215;
      mScratch128i[2] = 421;
      mScratch128i[3] = 423;
      mScratch128i[4] = 426;
      mScratch128i[5] = 429;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<6; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //654 218 {3.777570, 0.916189, 0.012817}
    //663 221 {3.777570, 0.916189, 0.012817}
    //669 223 {3.765511, -0.156670, 0.013755}
    //672 224 {3.744657, 0.295911, 0.013978}
    //678 226 {3.744657, 0.295917, 0.011655}
    //681 227 {3.765511, -0.156667, 0.011879}
    //687 229 {3.744657, 0.295911, 0.013978}
    //693 231 {3.744657, 0.295917, 0.011655}
    //711 237 {3.777570, 0.916189, 0.012817}
    //723 241 {3.777570, 0.916189, 0.012817}
    //1266 422 {3.765511, -0.156670, 0.013755}
    //1272 424 {3.765511, -0.474228, 0.012817}
    //1275 425 {3.765511, -0.156670, 0.013755}
    //1284 428 {3.765511, -0.156667, 0.011879}
    //1290 430 {3.765511, -0.156667, 0.011879}
    //1293 431 {3.765511, -0.474228, 0.012817}
    synchronized (mScratch128i) {
      mScratch128i[0] = 218;
      mScratch128i[1] = 221;
      mScratch128i[2] = 223;
      mScratch128i[3] = 224;
      mScratch128i[4] = 226;
      mScratch128i[5] = 227;
      mScratch128i[6] = 229;
      mScratch128i[7] = 231;
      mScratch128i[8] = 237;
      mScratch128i[9] = 241;
      mScratch128i[10] = 422;
      mScratch128i[11] = 424;
      mScratch128i[12] = 425;
      mScratch128i[13] = 428;
      mScratch128i[14] = 430;
      mScratch128i[15] = 431;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<16; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //690 230 {4.256256, 0.734284, 0.014600}
    //699 233 {4.256256, 0.734288, 0.011034}
    //705 235 {4.256256, 0.734284, 0.014600}
    //729 243 {4.256256, 0.734288, 0.011034}
    synchronized (mScratch128i) {
      mScratch128i[0] = 230;
      mScratch128i[1] = 233;
      mScratch128i[2] = 235;
      mScratch128i[3] = 243;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }
    //708 236 {5.630623, 1.450969, 0.012817}
    //717 239 {5.630623, 1.450969, 0.012817}
    //726 242 {5.630623, 1.450969, 0.012817}
    //735 245 {5.630623, 1.450969, 0.012817}
    synchronized (mScratch128i) {
      mScratch128i[0] = 236;
      mScratch128i[1] = 239;
      mScratch128i[2] = 242;
      mScratch128i[3] = 245;
      float width = getMoveWidth(ShumokuData.vertices[0+3*mScratch128i[0]]) * s;
      int ii;
      for (ii=0; ii<4; ii++) {
        ShumokuData.vertices[2+3*mScratch128i[ii]] = ShumokuData.org_vertices[2+3*mScratch128i[ii]] + width;
      }
    }

    mVertexBuffer.position(0);
    mVertexBuffer.put(ShumokuData.vertices);
    mVertexBuffer.position(0);
  }

  public void calc() {
    synchronized (this) {
      setTurnDirection(TURN_DIRECTION.STRAIGHT);
      think();
      //move();
      animate();
    }
  }

  public void draw(GL10 gl10) {
    gl10.glPushMatrix();


    gl10.glPushMatrix();
    {
      /*=======================================================================*/
      /* 環境光の材質色設定                                                    */
      /*=======================================================================*/
      synchronized (mScratch4f) {
        mScratch4f[0] = 0.07f;
        mScratch4f[1] = 0.07f;
        mScratch4f[2] = 0.07f;
        mScratch4f[3] = 1.0f;
        gl10.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_AMBIENT, mScratch4f, 0);
      }
      /*=======================================================================*/
      /* 拡散反射光の色設定                                                    */
      /*=======================================================================*/
      synchronized (mScratch4f) {
        mScratch4f[0] = 0.24f;
        mScratch4f[1] = 0.24f;
        mScratch4f[2] = 0.24f;
        mScratch4f[3] = 1.0f;
        gl10.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_DIFFUSE, mScratch4f, 0);
      }
      /*=======================================================================*/
      /* 鏡面反射光の質感色設定                                                */
      /*=======================================================================*/
      synchronized (mScratch4f) {
        mScratch4f[0] = 1.0f;
        mScratch4f[1] = 1.0f;
        mScratch4f[2] = 1.0f;
        mScratch4f[3] = 1.0f;
        gl10.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_SPECULAR, mScratch4f, 0);
      }
      gl10.glMaterialf(GL10.GL_FRONT_AND_BACK, GL10.GL_SHININESS, 64f);
    }
    gl10.glTranslatef(getX(),getY(),getZ());
    gl10.glScalef(GL_SHUMOKU_SCALE,GL_SHUMOKU_SCALE,GL_SHUMOKU_SCALE);

    gl10.glRotatef(angleForAnimation, 0.0f, 1.0f, 0.0f);

    gl10.glRotatef(y_angle, 0.0f, 1.0f, 0.0f);
    gl10.glRotatef(x_angle * -1f, 0.0f, 0.0f, 1.0f);

    // boundingboxを計算
    separateBoundingBox();
    alignment1BoundingBox();
    alignment2BoundingBox();

    gl10.glColor4f(1,1,1,1);
    gl10.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
    gl10.glNormalPointer(GL10.GL_FLOAT, 0, mNormalBuffer);
    gl10.glEnable(GL10.GL_TEXTURE_2D);
    gl10.glBindTexture(GL10.GL_TEXTURE_2D, textureIds[0]);
    gl10.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTextureBuffer);
    gl10.glDrawArrays(GL10.GL_TRIANGLES, 0, ShumokuData.numVerts);



    gl10.glPopMatrix();
    gl10.glPopMatrix();
  }

  public void update_speed() {
    sv_speed = speed;
    if (getStatus() == STATUS.COHESION || getStatus() == STATUS.TO_SCHOOL_CENTER || getStatus() == STATUS.TO_BAIT) {
      speed = cohesion_speed;
      return;
    }
    speed = sv_speed;

    if (this.rand.nextInt(10000) <= 1000) {
      // 変更なし
      return;
    }
    speed += (this.rand.nextFloat() * (speed_unit * 2f) / 2f);
    if (speed <= speed_min) {
      speed = speed_min;
    }
    if (speed > speed_max) {
      speed = speed_max;
    }
  }

  /**
   * どの方向に進むか考える
   */
  public void think() {
    long nowTime = System.nanoTime();
    if (prevTime != 0) {
      tick = nowTime - prevTime;
    }
    if (getStatus() == STATUS.COHESION || getStatus() == STATUS.TO_SCHOOL_CENTER || getStatus() == STATUS.TO_BAIT) {
      /* 元に戻す */
      speed = sv_speed;
    }
    prevTime = nowTime;
    if (  (Aquarium.min_x.floatValue() + (getSize() * 1.5f) >= position[0] || Aquarium.max_x.floatValue() - (getSize() * 1.5f) <= position[0])
      ||  (Aquarium.min_y.floatValue() + (getSize()/3f) >= position[1] || Aquarium.max_y.floatValue() - (getSize()/3f) <= position[1])
      ||  (Aquarium.min_z.floatValue() + (getSize() * 1.5f) >= position[2] || Aquarium.max_z.floatValue() - (getSize() * 1.5f) <= position[2])) {
      /*=====================================================================*/
      /* 水槽からはみ出てる                                                  */
      /*=====================================================================*/
      setStatus(STATUS.TO_CENTER);
      aimAquariumCenter();
      if (traceBOIDS && shumokuNo == 0) Log.d(TAG, "to Aquarium Center");
      update_speed();
      return;
    }
    /**
     * 餌ロジック
     */
    Bait bait = baitManager.getBait();
    if (bait != null) {
      if (this.rand.nextInt(10000) <= 5500) {
        if (aimBait(bait)) {
          if (traceBOIDS && shumokuNo == 0) Log.d(TAG, "to Bait");
          setStatus(STATUS.TO_BAIT);
          update_speed();
          return;
        }
      }
    }

    if (this.rand.nextInt(10000) <= 9500) {
      if (traceBOIDS && shumokuNo == 0) Log.d(TAG, "Nop");
      // 変更なし
      return;
    }
    setStatus(STATUS.NORMAL);
    turn();
    if (traceBOIDS && shumokuNo == 0) Log.d(TAG, "Normal");
    update_speed();
  }


  public void turn() {
    // 方向転換
    // 45 >= x >= -45
    // 360 >= y >= 0
    // 一回の方向転換のMAX
    // 45 >= x >= -45
    // 45 >= y >= -45
    float old_angle_x = x_angle;
    float old_angle_y = y_angle;
    x_angle = old_angle_x;
    y_angle = old_angle_y;
    float newAngleX = this.rand.nextFloat() * 3.0f - 1.5f;
    float newAngleY = 0f;
    if (angleForAnimation < 0f) {
      newAngleY = this.rand.nextFloat() * -1.5f;
      setTurnDirection(TURN_DIRECTION.TURN_RIGHT);
    }
    else {
      newAngleY = this.rand.nextFloat() * 1.5f;
      setTurnDirection(TURN_DIRECTION.TURN_LEFT);
    }
   
    y_angle = (float)((int)(y_angle + newAngleY) % 360);
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
  }
  public void aimTargetDegree(float angle_x, float angle_y) {
    float newAngle = this.rand.nextFloat() * 3f;

    float yy = angle_y - y_angle;
    if (yy > 180.0f) {
      yy = -360f + yy;
    }
    else if (yy < -180.0f) {
      yy = 360f - yy;
    }

    if (yy < 0.0f) {
      if (angleForAnimation < 0f) {
        if (yy > -1.5f) {
          y_angle += yy;
        }
        else {
          y_angle += -newAngle;
        }
        setTurnDirection(TURN_DIRECTION.TURN_LEFT);
      }
    }
    else if (yy > 0.0f) {
      if (angleForAnimation > 0f) {
        if (yy < 1.5f) {
          y_angle += yy;
        }
        else {
          y_angle += newAngle;
        }
        setTurnDirection(TURN_DIRECTION.TURN_RIGHT);
      }
    }
    else {
      setTurnDirection(TURN_DIRECTION.STRAIGHT);
    }
    y_angle = y_angle % 360f;
    if (y_angle < 0f) {
      y_angle = 360f + y_angle;
    }
  }
  public void aimTargetSpeed(float t_speed) {
    if (t_speed <= speed) {
      /* 自分のスピードよりも相手の方が遅い場合 */
      if (false) {
        speed -= (this.rand.nextFloat() * speed_unit);
        if (speed <= speed_min) {
          speed = speed_unit;
        }
      }
      else {
       update_speed();
      }
    }
    else {
      /* 相手の方が早い場合 */
      speed += (this.rand.nextFloat() * speed_unit);
      if (t_speed < speed) {
        /* 越えちゃったらちょっとだけ遅く*/
        speed = t_speed - (this.rand.nextFloat() * speed_unit);
      }
      if (speed > speed_max) {
        speed = speed_max;
      }
    }
  }
  public void turnSeparation(Shumoku target) {
    if (debug) { Log.d(TAG, "start turnSeparation"); }
    float v_x = 0f;
    float v_y = 0f;
    float v_z = 0f;
    synchronized (mScratch4f_1) {
      /*=======================================================================*/
      /* Separationしたいターゲットの方向取得                                  */
      /*=======================================================================*/
      mScratch4f_1[0] = target.getDirectionX();
      mScratch4f_1[1] = target.getDirectionY();
      mScratch4f_1[2] = target.getDirectionZ();
      CoordUtil.normalize3fv(mScratch4f_1);
      synchronized (mScratch4f_2) {
        /*=====================================================================*/
        /* ターゲットから見て、自分の方向を算出                                */
        /*=====================================================================*/
        mScratch4f_2[0] = getX() - target.getX();
        mScratch4f_2[1] = getY() - target.getY();
        mScratch4f_2[2] = getZ() - target.getZ();
        CoordUtil.normalize3fv(mScratch4f_2);
        /*=====================================================================*/
        /* ややターゲットの方向に沿いたいので、x2                              */
        /*=====================================================================*/
        mScratch4f_1[0] *= 2f;
        mScratch4f_1[1] *= 2f;
        mScratch4f_1[2] *= 2f;
        /*=====================================================================*/
        /* 足し込む                                                            */
        /*=====================================================================*/
        mScratch4f_1[0] += mScratch4f_2[0];
        mScratch4f_1[1] += mScratch4f_2[1];
        mScratch4f_1[2] += mScratch4f_2[2];
      }
      /*=====================================================================*/
      /* 平均算出                                                            */
      /*=====================================================================*/
      mScratch4f_1[0] /= 3f;
      mScratch4f_1[1] /= 3f;
      mScratch4f_1[2] /= 3f;

      v_x = mScratch4f_1[0];
      v_y = mScratch4f_1[1];
      v_z = mScratch4f_1[2];
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向"
       + " x:[" + v_x + "]:"
       + " y:[" + v_y + "]:"
       + " z:[" + v_z + "]:");
    }

    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    /* direction設定 */
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "結果的に向かう方向"
       + " x:[" + direction[0] + "]:"
       + " y:[" + direction[1] + "]:"
       + " z:[" + direction[2] + "]:");
      Log.d(TAG, "end turnSeparation");
    }
  }
  public void turnAlignment(Shumoku target) {
    if (debug) {
      Log.d(TAG, "start turnAlignment");
    }
    /* ターゲットの角度 */
    float angle_x = target.getX_angle();
    float angle_y = target.getY_angle();
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    /* direction設定 */
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "結果的に向かう方向"
       + " x:[" + direction[0] + "]:"
       + " y:[" + direction[1] + "]:"
       + " z:[" + direction[2] + "]:");
    }

    /* スピードも合わせる */
    aimTargetSpeed(target.getSpeed());

    if (debug) {
      Log.d(TAG, "end turnAlignment");
    }
  }
  public void turnCohesion(Shumoku target) {
    if (debug) { Log.d(TAG, "start turnCohesion"); }
    float v_x = 0f;
    float v_y = 0f;
    float v_z = 0f;
    synchronized (mScratch4f_1) {
      /*=======================================================================*/
      /* Separationしたいターゲットの方向取得                                  */
      /*=======================================================================*/
      mScratch4f_1[0] = target.getDirectionX();
      mScratch4f_1[1] = target.getDirectionY();
      mScratch4f_1[2] = target.getDirectionZ();
      CoordUtil.normalize3fv(mScratch4f_1);
      synchronized (mScratch4f_2) {
        /*=====================================================================*/
        /* 自分から見て、ターゲットの方向を算出                                */
        /*=====================================================================*/
        mScratch4f_2[0] = target.getX() - getX();
        mScratch4f_2[1] = target.getY() - getY();
        mScratch4f_2[2] = target.getZ() - getZ();
        CoordUtil.normalize3fv(mScratch4f_2);
        /*=====================================================================*/
        /* ややターゲットに近づきたいので x2                                   */
        /*=====================================================================*/
        mScratch4f_2[0] *= 2f;
        mScratch4f_2[1] *= 2f;
        mScratch4f_2[2] *= 2f;
        /*=====================================================================*/
        /* 足し込む                                                            */
        /*=====================================================================*/
        mScratch4f_1[0] += mScratch4f_2[0];
        mScratch4f_1[1] += mScratch4f_2[1];
        mScratch4f_1[2] += mScratch4f_2[2];
      }
      /*=====================================================================*/
      /* 平均算出                                                            */
      /*=====================================================================*/
      mScratch4f_1[0] /= 3f;
      mScratch4f_1[1] /= 3f;
      mScratch4f_1[2] /= 3f;

      v_x = mScratch4f_1[0];
      v_y = mScratch4f_1[1];
      v_z = mScratch4f_1[2];
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向"
       + " x:[" + v_x + "]:"
       + " y:[" + v_y + "]:"
       + " z:[" + v_z + "]:");
    }


    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    /* direction設定 */
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "結果的に向かう方向"
       + " x:[" + direction[0] + "]:"
       + " y:[" + direction[1] + "]:"
       + " z:[" + direction[2] + "]:");
      Log.d(TAG, "end turnCohesion");
    }
  }

  /**
   * 強制的に水槽の中心へ徐々に向ける
   */
  public void aimAquariumCenter() {
    if (debug) {
      Log.d(TAG, "start aimAquariumCenter ");
    }
    float v_x = (Aquarium.center[0] - getX());
    float v_y = (Aquarium.center[1] - getY());
    float v_z = (Aquarium.center[2] - getZ());
    if (Aquarium.min_x.floatValue() + (getSize() * 1.5f)    < getX() && Aquarium.max_x.floatValue() - (getSize() * 1.5f)    > getX()
    &&  Aquarium.min_y.floatValue() + (getSize()/3f) < getY() && Aquarium.max_y.floatValue() - (getSize()/3f) > getY()) {
      /* Zだけはみ出た */
      v_x = 0.0f;
      v_y = 0.0f;
    }
    else 
    if (Aquarium.min_x.floatValue() + (getSize() * 1.5f) < getX() && Aquarium.max_x.floatValue() - (getSize() * 1.5f) > getX()
    &&  Aquarium.min_z.floatValue() + (getSize() * 1.5f) < getZ() && Aquarium.max_z.floatValue() - (getSize() * 1.5f) > getZ()) {
      /* Yだけはみ出た */
      v_x = 0.0f;
      v_z = 0.0f;
    }
    else 
    if (Aquarium.min_y.floatValue() + (getSize()/3f)      < getY() && Aquarium.max_y.floatValue() - (getSize()/3f)     > getY()
    &&  Aquarium.min_z.floatValue() + (getSize() * 1.5f) < getZ() && Aquarium.max_z.floatValue() - (getSize() * 1.5f) > getZ()) {
      /* Xだけはみ出た */
      v_y = 0.0f;
      v_z = 0.0f;
    }
    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    if (angle_y < 0.0f) {
      angle_y = 360f + angle_y;
    }
    angle_y = angle_y % 360f;

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "end aimAquariumCenter "
        + "x:[" + direction[0] + "]:"
        + "y:[" + direction[1] + "]:"
        + "z:[" + direction[2] + "]:");
    }
  }
  public void aimSchoolCenter() {
    if (debug) {
      Log.d(TAG, "start aimSchoolCenter ");
    }

    float v_x = 0f;
    float v_y = 0f;
    float v_z = 0f;
    synchronized (mScratch4f_1) {
      /*=======================================================================*/
      /* 向かいたいschoolの方向取得                                            */
      /*=======================================================================*/
      mScratch4f_1[0] = schoolDir[0];
      mScratch4f_1[1] = schoolDir[1];
      mScratch4f_1[2] = schoolDir[2];
      CoordUtil.normalize3fv(mScratch4f_1);
      synchronized (mScratch4f_2) {
        /*=====================================================================*/
        /* 自分から見て、ターゲットの方向を算出                                */
        /*=====================================================================*/
        mScratch4f_2[0] = schoolCenter[0] - getX();
        mScratch4f_2[1] = schoolCenter[1] - getY();
        mScratch4f_2[2] = schoolCenter[2] - getZ();
        CoordUtil.normalize3fv(mScratch4f_2);
        /*=====================================================================*/
        /* ややターゲットに近づきたいので x2                                   */
        /*=====================================================================*/
        mScratch4f_2[0] *= 2f;
        mScratch4f_2[1] *= 2f;
        mScratch4f_2[2] *= 2f;
        /*=====================================================================*/
        /* 足し込む                                                            */
        /*=====================================================================*/
        mScratch4f_1[0] += mScratch4f_2[0];
        mScratch4f_1[1] += mScratch4f_2[1];
        mScratch4f_1[2] += mScratch4f_2[2];
      }
      /*=====================================================================*/
      /* 平均算出                                                            */
      /*=====================================================================*/
      mScratch4f_1[0] /= 3f;
      mScratch4f_1[1] /= 3f;
      mScratch4f_1[2] /= 3f;

      v_x = mScratch4f_1[0];
      v_y = mScratch4f_1[1];
      v_z = mScratch4f_1[2];
    }
    //float v_x = (schoolCenter[0] - getX());
    //float v_y = (schoolCenter[1] - getY());
    //float v_z = (schoolCenter[2] - getZ());

    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    if (angle_y < 0.0f) {
      angle_y = 360f + angle_y;
    }
    angle_y = angle_y % 360f;

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "end aimSchoolCenter "
        + "x:[" + direction[0] + "]:"
        + "y:[" + direction[1] + "]:"
        + "z:[" + direction[2] + "]:");
    }
  }
  public boolean aimBait(Bait bait) {
    if (debug) {
      Log.d(TAG, "start aimBait ");
    }
    double dist = Math.sqrt(
        Math.pow(position[0]-bait.getX(), 2)
      + Math.pow(position[1]-bait.getY(), 2)
      + Math.pow(position[2]-bait.getZ(), 2));
    if (dist <= separate_dist) {
      baitManager.eat(bait);
      return false;
    }
    float v_x = (bait.getX() - getX());
    float v_y = (bait.getY() - getY());
    float v_z = (bait.getZ() - getZ());
    if (debug) {
      Log.d(TAG, "向かいたい方向"
       + " x:[" + v_x + "]:"
       + " y:[" + v_y + "]:"
       + " z:[" + v_z + "]:");
    }

    /* 上下角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_x = (float)coordUtil.convertDegreeXY((double)v_x, (double)v_y);
    /* 左右角度算出 (-1dを乗算しているのは0度の向きが違うため) */
    float angle_y = (float)coordUtil.convertDegreeXZ((double)v_x * -1d, (double)v_z);
    if (angle_x > 180f) {
      angle_x = angle_x - 360f;
    }
    if ((angle_x < 0.0f && v_y > 0.0f) || (angle_x > 0.0f && v_y < 0.0f)) {
      angle_x *= -1f;
    }
    if (debug) {
      Log.d(TAG, "向かいたい方向のangle_y:[" + angle_y + "]");
      Log.d(TAG, "向かいたい方向のangle_x:[" + angle_x + "]");
    }

    /* その角度へ近づける */
    aimTargetDegree(angle_x, angle_y);
    if (debug) {
      Log.d(TAG, "実際に向かう方向のy_angle:[" + y_angle + "]");
      Log.d(TAG, "実際に向かう方向のx_angle:[" + x_angle + "]");
    }

    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine(-1.0f,0.0f, 0.0f, mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        direction[0] = mScratch4f_2[0];
        direction[1] = mScratch4f_2[1];
        direction[2] = mScratch4f_2[2];
      }
    }
    if (debug) {
      Log.d(TAG, "end aimBait "
        + "x:[" + direction[0] + "]:"
        + "y:[" + direction[1] + "]:"
        + "z:[" + direction[2] + "]:");
    }
    return true;
  }
  public void move() {
    /*=======================================================================*/
    /* 処理速度を考慮した増分                                                */
    /*=======================================================================*/
    float moveWidth = getSpeed() * (float)(tick / BASE_TICK);

    if (getX() + getDirectionX() * moveWidth >= Aquarium.max_x) {
      setX(Aquarium.max_x);
    }
    else if (getX() + getDirectionX() * moveWidth <= Aquarium.min_x) {
      setX(Aquarium.min_x);
    }
    else {
      setX(getX() + getDirectionX() * moveWidth);
    }
    if (getY() + getDirectionY() * moveWidth >= Aquarium.max_y) {
      setY(Aquarium.max_y);
    }
    else if (getY() + getDirectionY() * moveWidth <= Aquarium.min_y) {
      setY(Aquarium.min_y);
    }
    else {
      setY(getY() + getDirectionY() * moveWidth);
    }
    if (getZ() + getDirectionZ() * moveWidth >= Aquarium.max_z) {
      setZ(Aquarium.max_z);
    }
    else if (getZ() + getDirectionZ() * moveWidth <= Aquarium.min_z) {
      setZ(Aquarium.min_z);
    }
    else {
      setZ(getZ() + getDirectionZ() * moveWidth);
    }
    if (debug) {
      Log.d(TAG, "end move "
        + "dx:[" + getDirectionX() + "]:"
        + "dy:[" + getDirectionY() + "]:"
        + "dz:[" + getDirectionZ() + "]:"
        + "speed:[" + getSpeed() + "]:"
        + "x:[" + getX() + "]:"
        + "y:[" + getY() + "]:"
        + "z:[" + getZ() + "]:"
        + "x_angle:[" + x_angle + "]:"
        + "y_angle:[" + y_angle + "]:"
        );
    }
  }


  public float[] getPosition() {
    return position;
  }
  public void setPosition(float[] pos) {
    this.position = pos;
  }
  
  public float getX() {
    return position[0];
  }
  
  public void setX(float x) {
    this.position[0] = x;
  }
  
  public float getY() {
    return position[1];
  }
  
  public void setY(float y) {
    this.position[1] = y;
  }
  
  public float getZ() {
    return position[2];
  }
  
  public void setZ(float z) {
    this.position[2] = z;
  }

  public float getDirectionX() {
    return direction[0];
  }
  public float getDirectionY() {
    return direction[1];
  }
  public float getDirectionZ() {
    return direction[2];
  }
  public void setDirectionX(float x) {
    this.direction[0] = x;
  }
  public void setDirectionY(float y) {
    this.direction[1] = y;
  }
  public void setDirectionZ(float z) {
    this.direction[2] = z;
  }
  
  public float getSpeed() {
    return speed;
  }
  
  public void setSpeed(float speed) {
    this.speed = speed * 0.5f;
    this.speed_unit = speed / 5f * 0.5f;
    this.speed_max = speed * 3f * 0.5f;
    this.speed_min = this.speed_unit * 2f;
    this.cohesion_speed = speed * 5f * 0.5f;
    this.sv_speed = speed;
  }
  
  public float[] getDirection() {
    return direction;
  }
  
  public float getDirection(int index) {
    return direction[index];
  }
  
  public void setDirection(float[] direction) {
    this.direction = direction;
  }
  
  public void setDirection(float direction, int index) {
    this.direction[index] = direction;
  }
  
  
  /**
   * Get x_angle.
   *
   * @return x_angle as float.
   */
  public float getX_angle()
  {
      return x_angle;
  }
  
  /**
   * Set x_angle.
   *
   * @param x_angle the value to set.
   */
  public void setX_angle(float x_angle)
  {
      this.x_angle = x_angle;
  }
  
  /**
   * Get y_angle.
   *
   * @return y_angle as float.
   */
  public float getY_angle()
  {
      return y_angle;
  }
  
  /**
   * Set y_angle.
   *
   * @param y_angle the value to set.
   */
  public void setY_angle(float y_angle)
  {
      this.y_angle = y_angle;
  }
  
  /**
   * Get schoolCenter.
   *
   * @return schoolCenter as float[].
   */
  public float[] getSchoolCenter()
  {
      return schoolCenter;
  }
  
  /**
   * Get schoolCenter element at specified index.
   *
   * @param index the index.
   * @return schoolCenter at index as float.
   */
  public float getSchoolCenter(int index)
  {
      return schoolCenter[index];
  }
  
  /**
   * Set schoolCenter.
   *
   * @param schoolCenter the value to set.
   */
  public void setSchoolCenter(float[] schoolCenter) {
      this.schoolCenter = schoolCenter;
  }
  
  /**
   * Set schoolCenter at the specified index.
   *
   * @param schoolCenter the value to set.
   * @param index the index.
   */
  public void setSchoolCenter(float schoolCenter, int index)
  {
      this.schoolCenter[index] = schoolCenter;
  }
  
  /**
   * Get baitManager.
   *
   * @return baitManager as BaitManager.
   */
  public BaitManager getBaitManager()
  {
      return baitManager;
  }
  
  /**
   * Set baitManager.
   *
   * @param baitManager the value to set.
   */
  public void setBaitManager(BaitManager baitManager)
  {
      this.baitManager = baitManager;
  }
  
  
  /**
   * Get enableBoids.
   *
   * @return enableBoids as boolean.
   */
  public boolean getEnableBoids()
  {
      return enableBoids;
  }
  
  /**
   * Set enableBoids.
   *
   * @param enableBoids the value to set.
   */
  public void setEnableBoids(boolean enableBoids)
  {
      this.enableBoids = enableBoids;
  }
  
  /**
   * Get status.
   *
   * @return status as STATUS.
   */
  public STATUS getStatus() {
    return status;
  }
  
  /**
   * Set status.
   *
   * @param status the value to set.
   */
  public void setStatus(STATUS status) {
    this.status = status;
  }
  
  /**
   * Get size.
   *
   * @return size as float.
   */
  public float getSize()
  {
      return size;
  }
  
  /**
   * Set size.
   *
   * @param size the value to set.
   */
  public void setSize(float size)
  {
      this.size = size;
  }
  
  /**
   * Get shumokuCount.
   *
   * @return shumokuCount as int.
   */
  public int getShumokuCount()
  {
      return shumokuCount;
  }
  
  /**
   * Set shumokuCount.
   *
   * @param shumokuCount the value to set.
   */
  public void setShumokuCount(int shumokuCount)
  {
      this.shumokuCount = shumokuCount;
  }
  
  /**
   * Get distances.
   *
   * @return distances as float[].
   */
  public float[] getDistances()
  {
      return distances;
  }
  
  /**
   * Get distances element at specified index.
   *
   * @param index the index.
   * @return distances at index as float.
   */
  public float getDistances(int index)
  {
      return distances[index];
  }
  
  /**
   * Set distances.
   *
   * @param distances the value to set.
   */
  public void setDistances(float[] distances)
  {
      this.distances = distances;
  }
  
  /**
   * Set distances at the specified index.
   *
   * @param distances the value to set.
   * @param index the index.
   */
  public void setDistances(float distances, int index)
  {
      this.distances[index] = distances;
  }

  public void separateBoundingBox() {
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[0],(float)aabb_org[1], (float)aabb_org[2], mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        sep_aabb[0] = mScratch4f_2[0];
        sep_aabb[1] = mScratch4f_2[1];
        sep_aabb[2] = mScratch4f_2[2];
      }
    }
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[3],(float)aabb_org[4], (float)aabb_org[5], mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        sep_aabb[3] = mScratch4f_2[0];
        sep_aabb[4] = mScratch4f_2[1];
        sep_aabb[5] = mScratch4f_2[2];
      }
    }
    if (sep_aabb[0] > sep_aabb[3]) {
      double tmp = sep_aabb[0];
      sep_aabb[0] = sep_aabb[3];
      sep_aabb[3] = tmp;
    }
    if (sep_aabb[1] > sep_aabb[4]) {
      double tmp = sep_aabb[1];
      sep_aabb[1] = sep_aabb[4];
      sep_aabb[4] = tmp;
    }
    if (sep_aabb[2] > sep_aabb[5]) {
      double tmp = sep_aabb[2];
      sep_aabb[2] = sep_aabb[5];
      sep_aabb[5] = tmp;
    }
    sep_aabb[0] += getX();
    sep_aabb[1] += getY();
    sep_aabb[2] += getZ();
    sep_aabb[3] += getX();
    sep_aabb[4] += getY();
    sep_aabb[5] += getZ();
  }

  public static boolean crossTestSep(float x, float y, float z) {
    double min_x = sep_aabb[0];
    double min_y = sep_aabb[1];
    double min_z = sep_aabb[2];
    double max_x = sep_aabb[3];
    double max_y = sep_aabb[4];
    double max_z = sep_aabb[5];
    return (   (float)min_x <= x && (float)max_x >= x
            && (float)min_y <= y && (float)max_y >= y
            && (float)min_z <= z && (float)max_z >= z);
  }

  public void alignment1BoundingBox() {
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[0] - (float)alignment_dist1,
                         (float)aabb_org[1] - (float)alignment_dist1, 
                         (float)aabb_org[2] - (float)alignment_dist1, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        al1_aabb[0] = mScratch4f_2[0];
        al1_aabb[1] = mScratch4f_2[1];
        al1_aabb[2] = mScratch4f_2[2];
      }
    }
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[3] + (float)alignment_dist1,
                         (float)aabb_org[4] + (float)alignment_dist1, 
                         (float)aabb_org[5] + (float)alignment_dist1, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        al1_aabb[3] = mScratch4f_2[0];
        al1_aabb[4] = mScratch4f_2[1];
        al1_aabb[5] = mScratch4f_2[2];
      }
    }
    if (al1_aabb[0] > al1_aabb[3]) {
      double tmp = al1_aabb[0];
      al1_aabb[0] = al1_aabb[3];
      al1_aabb[3] = tmp;
    }
    if (al1_aabb[1] > al1_aabb[4]) {
      double tmp = al1_aabb[1];
      al1_aabb[1] = al1_aabb[4];
      al1_aabb[4] = tmp;
    }
    if (al1_aabb[2] > al1_aabb[5]) {
      double tmp = al1_aabb[2];
      al1_aabb[2] = al1_aabb[5];
      al1_aabb[5] = tmp;
    }
    al1_aabb[0] += getX();
    al1_aabb[1] += getY();
    al1_aabb[2] += getZ();
    al1_aabb[3] += getX();
    al1_aabb[4] += getY();
    al1_aabb[5] += getZ();
  }
  public static boolean crossTestAl1(float x, float y, float z) {
    double min_x = al1_aabb[0];
    double min_y = al1_aabb[1];
    double min_z = al1_aabb[2];
    double max_x = al1_aabb[3];
    double max_y = al1_aabb[4];
    double max_z = al1_aabb[5];
    return (   (float)min_x <= x && (float)max_x >= x
            && (float)min_y <= y && (float)max_y >= y
            && (float)min_z <= z && (float)max_z >= z);
  }
  public void alignment2BoundingBox() {
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[0] - (float)alignment_dist2,
                         (float)aabb_org[1] - (float)alignment_dist2, 
                         (float)aabb_org[2] - (float)alignment_dist2, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        al2_aabb[0] = mScratch4f_2[0];
        al2_aabb[1] = mScratch4f_2[1];
        al2_aabb[2] = mScratch4f_2[2];
      }
    }
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[3] + (float)alignment_dist2,
                         (float)aabb_org[4] + (float)alignment_dist2, 
                         (float)aabb_org[5] + (float)alignment_dist2, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        al2_aabb[3] = mScratch4f_2[0];
        al2_aabb[4] = mScratch4f_2[1];
        al2_aabb[5] = mScratch4f_2[2];
      }
    }
    if (al2_aabb[0] > al2_aabb[3]) {
      double tmp = al2_aabb[0];
      al2_aabb[0] = al2_aabb[3];
      al2_aabb[3] = tmp;
    }
    if (al2_aabb[1] > al2_aabb[4]) {
      double tmp = al2_aabb[1];
      al2_aabb[1] = al2_aabb[4];
      al2_aabb[4] = tmp;
    }
    if (al2_aabb[2] > al2_aabb[5]) {
      double tmp = al2_aabb[2];
      al2_aabb[2] = al2_aabb[5];
      al2_aabb[5] = tmp;
    }
    al2_aabb[0] += getX();
    al2_aabb[1] += getY();
    al2_aabb[2] += getZ();
    al2_aabb[3] += getX();
    al2_aabb[4] += getY();
    al2_aabb[5] += getZ();
  }
  public static boolean crossTestAl2(float x, float y, float z) {
    double min_x = al2_aabb[0];
    double min_y = al2_aabb[1];
    double min_z = al2_aabb[2];
    double max_x = al2_aabb[3];
    double max_y = al2_aabb[4];
    double max_z = al2_aabb[5];
    return (   (float)min_x <= x && (float)max_x >= x
            && (float)min_y <= y && (float)max_y >= y
            && (float)min_z <= z && (float)max_z >= z);
  }
  public void schoolBoundingBox() {
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[0] - (float)school_dist,
                         (float)aabb_org[1] - (float)school_dist, 
                         (float)aabb_org[2] - (float)school_dist, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        sch_aabb[0] = mScratch4f_2[0];
        sch_aabb[1] = mScratch4f_2[1];
        sch_aabb[2] = mScratch4f_2[2];
      }
    }
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[3] + (float)school_dist,
                         (float)aabb_org[4] + (float)school_dist, 
                         (float)aabb_org[5] + (float)school_dist, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        sch_aabb[3] = mScratch4f_2[0];
        sch_aabb[4] = mScratch4f_2[1];
        sch_aabb[5] = mScratch4f_2[2];
      }
    }
    if (sch_aabb[0] > sch_aabb[3]) {
      double tmp = sch_aabb[0];
      sch_aabb[0] = sch_aabb[3];
      sch_aabb[3] = tmp;
    }
    if (sch_aabb[1] > sch_aabb[4]) {
      double tmp = sch_aabb[1];
      sch_aabb[1] = sch_aabb[4];
      sch_aabb[4] = tmp;
    }
    if (sch_aabb[2] > sch_aabb[5]) {
      double tmp = sch_aabb[2];
      sch_aabb[2] = sch_aabb[5];
      sch_aabb[5] = tmp;
    }
    sch_aabb[0] += getX();
    sch_aabb[1] += getY();
    sch_aabb[2] += getZ();
    sch_aabb[3] += getX();
    sch_aabb[4] += getY();
    sch_aabb[5] += getZ();
  }
  public static boolean crossTestSch(float x, float y, float z) {
    double min_x = sch_aabb[0];
    double min_y = sch_aabb[1];
    double min_z = sch_aabb[2];
    double max_x = sch_aabb[3];
    double max_y = sch_aabb[4];
    double max_z = sch_aabb[5];
    return (   (float)min_x <= x && (float)max_x >= x
            && (float)min_y <= y && (float)max_y >= y
            && (float)min_z <= z && (float)max_z >= z);
  }
  public void cohesionBoundingBox() {
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[0] - (float)cohesion_dist,
                         (float)aabb_org[1] - (float)cohesion_dist, 
                         (float)aabb_org[2] - (float)cohesion_dist, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        coh_aabb[0] = mScratch4f_2[0];
        coh_aabb[1] = mScratch4f_2[1];
        coh_aabb[2] = mScratch4f_2[2];
      }
    }
    coordUtil.setMatrixRotateZ(x_angle);
    synchronized (mScratch4f_1) {
      synchronized (mScratch4f_2) {
        coordUtil.affine((float)aabb_org[3] + (float)cohesion_dist,
                         (float)aabb_org[4] + (float)cohesion_dist, 
                         (float)aabb_org[5] + (float)cohesion_dist, 
                         mScratch4f_1);
        coordUtil.setMatrixRotateY(y_angle);
        coordUtil.affine(mScratch4f_1[0],mScratch4f_1[1], mScratch4f_1[2], mScratch4f_2);
        coh_aabb[3] = mScratch4f_2[0];
        coh_aabb[4] = mScratch4f_2[1];
        coh_aabb[5] = mScratch4f_2[2];
      }
    }
    if (coh_aabb[0] > coh_aabb[3]) {
      double tmp = coh_aabb[0];
      coh_aabb[0] = coh_aabb[3];
      coh_aabb[3] = tmp;
    }
    if (coh_aabb[1] > coh_aabb[4]) {
      double tmp = coh_aabb[1];
      coh_aabb[1] = coh_aabb[4];
      coh_aabb[4] = tmp;
    }
    if (coh_aabb[2] > coh_aabb[5]) {
      double tmp = coh_aabb[2];
      coh_aabb[2] = coh_aabb[5];
      coh_aabb[5] = tmp;
    }
    coh_aabb[0] += getX();
    coh_aabb[1] += getY();
    coh_aabb[2] += getZ();
    coh_aabb[3] += getX();
    coh_aabb[4] += getY();
    coh_aabb[5] += getZ();
  }
  public static boolean crossTestCoh(float x, float y, float z) {
    double min_x = coh_aabb[0];
    double min_y = coh_aabb[1];
    double min_z = coh_aabb[2];
    double max_x = coh_aabb[3];
    double max_y = coh_aabb[4];
    double max_z = coh_aabb[5];
    return (   (float)min_x <= x && (float)max_x >= x
            && (float)min_y <= y && (float)max_y >= y
            && (float)min_z <= z && (float)max_z >= z);
  }
  
  /**
   * Get turnDirection.
   *
   * @return turnDirection as TURN_DIRECTION.
   */
  public TURN_DIRECTION getTurnDirection()
  {
      return turnDirection;
  }
  
  /**
   * Set turnDirection.
   *
   * @param turnDirection the value to set.
   */
  public void setTurnDirection(TURN_DIRECTION turnDirection)
  {
      this.turnDirection = turnDirection;
  }
}
