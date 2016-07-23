#include <sys/time.h>
#include <stdio.h>
#include <math.h>

#define R    5
#define N    30208

#define VIRTUAL	/* virtual */   // unnecessary due to no subclasses

long wtime() {
    struct timeval time;
    gettimeofday(&time, NULL);
    return time.tv_sec * 1000000 + time.tv_usec;
}

float reciprocalSqrt(float f) {
    return 1.0f / sqrtf(f);
}

class Vec3 {
public:
    float x, y, z;

    Vec3(float x0, float y0, float z0) :
            x(x0), y(y0), z(z0) {
    }
    Vec3(const Vec3& v) :
            x(v.x), y(v.y), z(v.z) {
    }

    Vec3 add(const Vec3 v) const {
        return Vec3(x + v.x, y + v.y, z + v.z);
    }

    Vec3 sub(const Vec3 v) const {
        return Vec3(x - v.x, y - v.y, z - v.z);
    }

    float mult(const Vec3 v) const {
        return x * v.x + y * v.y + z * v.z;
    }

    Vec3 scale(float s) const {
        return Vec3(x * s, y * s, z * s);
    }
};

class Vec4 {
public:
    const float x, y, z, w;

    Vec4(float x0, float y0, float z0, float w0) :
            x(x0), y(y0), z(z0), w(w0) {
    }
};

class Vec4Func {
public:
    virtual Vec4 apply(int i) const = 0;
};

class Func {
public:
    virtual Vec3 apply(class Vec4Array* a, int i, const Vec3 v,
            const float w) const = 0;
};

class Vec4Array {
public:
    float* const elements;
    const int size;

public:
    Vec4Array(int n) :
            elements(new float[n * 4]), size(n) {
    }

    VIRTUAL Vec3 get(int i) {
        return Vec3(elements[i * 4], elements[i * 4 + 1], elements[i * 4 + 2]);
    }

    VIRTUAL float getW(int i) {
        return elements[i * 4 + 3];
    }

    VIRTUAL void set(int i, const Vec3 v) {
        elements[i * 4] = v.x;
        elements[i * 4 + 1] = v.y;
        elements[i * 4 + 2] = v.z;
    }

    VIRTUAL
    void set(int i, const Vec4 v) {
        elements[i * 4] = v.x;
        elements[i * 4 + 1] = v.y;
        elements[i * 4 + 2] = v.z;
        elements[i * 4 + 3] = v.w;
    }

    void tabulate(const Vec4Func* f) {
        for (int i = 0; i < size; i++)
            set(i, f->apply(i));
    }

    void map(const Func* const f, Vec4Array* const src) {
        for (int i = 0; i < size; i++)
            set(i, f->apply(src, i, src->get(i), src->getW(i)));
    }

    Vec3 sum(const Func* const f) {
        Vec3 v(0, 0, 0);
        for (int i = 0; i < size; i++) {
            Vec3 v2 = f->apply(this, i, get(i), getW(i));
            v = v.add(v2);
        }

        return v;
    }
};

float gflops(long msec) {
    int fpi = 20;
    float instsec = (float) N * (float) N;
    instsec *= 1e-9 * R * 2 * 1000 / msec;
    return instsec * (float) fpi;
}

void printTime(long t1, long t2) {
    long msec = (t2 - t1) / 1000;
    printf("%ld msec, %f GFlops\n", msec, gflops(msec));
}

class MyFuncForSum: public Func {
    const Vec3 pi;

public:
    MyFuncForSum(Vec3 _pi) :
            pi(_pi) {
    }

    virtual Vec3 apply(Vec4Array* pos, int i, const Vec3 pj, float wj) const {
        Vec3 r = pi.sub(pj);
        float ra = reciprocalSqrt(r.mult(r) + 0.01f);
        return r.scale(wj * (ra * ra * ra));
    }
};

class MyFuncForMap: public Func {
    Vec4Array* const vel;

public:
    MyFuncForMap(Vec4Array* v) :
            vel(v) {
    }

    virtual Vec3 apply(Vec4Array* pos, int i, const Vec3 pi,
            const float w) const {
        MyFuncForSum func(pi);
        Vec3 a = pos->sum(&func);
        Vec3 v2 = (vel->get(i)).add(a.scale(0.016f)).scale(1.0f);
        vel->set(i, v2);
        return pi.add(v2.scale(0.016f));
    }
};

class MyVec4Func: public Vec4Func {
public:
    MyVec4Func() {
    }

    virtual Vec4 apply(int i) const {
        return Vec4(i, i, i, 2);
    }
};

class MyFuncForReduction: public Func {
public:
    virtual Vec3 apply(Vec4Array* pos, int i, const Vec3 v,
            const float w) const {
        return Vec3(v.x / w, v.y / w, v.z / w);
    }
};

int main() {
    Vec4Array* const pos1 = new Vec4Array(N);
    Vec4Array* const pos2 = new Vec4Array(N);
    Vec4Array* const vel = new Vec4Array(N);

    const MyVec4Func init;
    const MyFuncForMap f(vel);

    pos1->tabulate(&init);
    vel->tabulate(&init);

    long t1 = wtime();
    for (int i = 0; i < R; i++) {
        pos2->map(&f, pos1);
        pos1->map(&f, pos2);
    }

    long t2 = wtime();
    printTime(t1, t2);

    MyFuncForReduction f2;

    Vec3 g = pos1->sum(&f2);
    printf("%f, %f, %f\n", g.x / N, g.y / N, g.z / N);
    return 0;
}
