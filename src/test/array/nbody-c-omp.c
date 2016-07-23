#include <sys/time.h>
#include <stdio.h>
#include <math.h>

#define R    5
#define N    30208

typedef struct {
  float x, y, z, w;
} Vec4;

typedef struct {
  float x, y, z;
} Vec3;

static Vec4 pos1[N];
static Vec4 pos2[N];
static Vec4 vel[N];

long wtime() {
  struct timeval time;
  gettimeofday(&time, NULL);
  return time.tv_sec * 1000000 + time.tv_usec;
}

void init() {
  for (int i = 0; i < N; i++) {
    pos1[i].x = i;
    pos1[i].y = i;
    pos1[i].z = i;
    pos1[i].w = 2;
    pos2[i].x = i;
    pos2[i].y = i;
    pos2[i].z = i;
    pos2[i].w = 2;
    vel[i].x = i;
    vel[i].y = i;
    vel[i].z = i;
    vel[i].w = 2;
  }
}

void map1() {
#pragma omp parallel for
  for (int i = 0; i < N; i++) {
    float px = pos1[i].x;
    float py = pos1[i].y;
    float pz = pos1[i].z;

    float ax = 0;
    float ay = 0;
    float az = 0;
    for (int j = 0; j < N; j++) {
      float rx = px - pos1[j].x;
      float ry = py - pos1[j].y;
      float rz = pz - pos1[j].z;
      float ra = 1.0f / sqrtf(rx * rx + ry * ry + rz * rz + 0.01f);
      float s = pos1[i].w * (ra * ra * ra);
      ax += rx * s;
      ay += ry * s;
      az += rz * s;
    }

    float x = vel[i].x = (vel[i].x + ax * 0.016f) * 1.0f;
    float y = vel[i].y = (vel[i].y + ay * 0.016f) * 1.0f;
    float z = vel[i].z = (vel[i].z + az * 0.016f) * 1.0f;
    pos2[i].x = px + x * 0.016f;
    pos2[i].y = py + y * 0.016f;
    pos2[i].z = pz + z * 0.016f;
  }
}

void map2() {
#pragma omp parallel for
  for (int i = 0; i < N; i++) {
    float px = pos2[i].x;
    float py = pos2[i].y;
    float pz = pos2[i].z;

    float ax = 0;
    float ay = 0;
    float az = 0;
    for (int j = 0; j < N; j++) {
      float rx = px - pos2[j].x;
      float ry = py - pos2[j].y;
      float rz = pz - pos2[j].z;

      /* if the compiler is icc, the following code is better than:
       *    float ra = sqrt(...);
       *    float s = pos2[i].w / (ra * ra * ra);
       */
      float ra = 1.0f / sqrtf(rx * rx + ry * ry + rz * rz + 0.01f);
      float s = pos2[i].w * (ra * ra * ra);

      ax += rx * s;
      ay += ry * s;
      az += rz * s;
    }

    float x = vel[i].x = (vel[i].x + ax * 0.016f) * 1.0f;
    float y = vel[i].y = (vel[i].y + ay * 0.016f) * 1.0f;
    float z = vel[i].z = (vel[i].z + az * 0.016f) * 1.0f;
    pos1[i].x = px + x * 0.016f;
    pos1[i].y = py + y * 0.016f;
    pos1[i].z = pz + z * 0.016f;
  }
}

Vec3 reduce() {
  Vec3 av;
  av.x = av.y = av.z = 0;
  for (int i = 0; i < N; i++) {
    float w = pos1[i].w;
    av.x += pos1[i].x / w;
    av.y += pos1[i].y / w;
    av.z += pos1[i].z / w;
  }

  return av;
}

void dump(Vec4* vecs) {
  for (int i = 0; i < N; i++)
    printf("%lg ", vecs[i].x);

  printf("\n");
}

float gflops(long msec) {
  int fpi = 20;
  float instsec = (float)N * (float)N;
  instsec *= 1e-9 * R * 2 * 1000 / msec;
  return instsec * (float)fpi;
}

void printTime(long t1, long t2) {
  long msec = (t2 - t1) / 1000;
  printf("%ld msec, %f GFlops\n", msec, gflops(msec));
}

int main() {
  init();

  long t1 = wtime();
  for (int i = 0; i < R; i++) {
    map1();
    map2();
  }
  long t2 = wtime();
  printTime(t1, t2);

  Vec3 g = reduce();
  printf("%f, %f, %f\n", g.x / N, g.y / N, g.z / N);
  return 0;
}
