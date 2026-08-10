# Native libraries — arm64-v8a

Is folder me sherpa-onnx ki do native library files aati hain:

- `libonnxruntime.so`  (~15 MB)
- `libsherpa-onnx-jni.so`  (~4 MB)

## Ye files kahan se laayein

1. https://github.com/k2-fsa/sherpa-onnx/releases kholo
2. Sabse naya `v1.x.x` release chuno (`flutter` wala release NAHI — usme sirf demo apps hain)
3. Assets me se `sherpa-onnx-v1.x.x-android.tar.bz2` download karo
4. Extract karo (7-Zip / WinRAR, do baar)
5. `jniLibs/arm64-v8a/` folder ke andar se upar wali dono `.so` files uthao
6. Unhe isi folder me upload kar do

## Ye git me kyun rakhi hain

APK GitHub Actions pe banti hai, kisi ke computer pe nahi. Isliye native
libraries repo ke andar honi zaroori hain, warna CI ke paas ye hoti hi nahi.

## Agar ye files na hon to kya hoga

App phir bhi theek se banegi aur chalegi. Sirf offline wake word (sherpa-onnx)
wala option kaam nahi karega — app apne aap Google recognizer pe wapas chali
jayegi aur Settings me bata degi ki library missing hai.

## Doosre phone architectures

Abhi sirf `arm64-v8a` support hai, jo aaj ke lagbhag saare phones me hota hai.
Bahut purane 32-bit phones ke liye `app/src/main/jniLibs/armeabi-v7a/` banakar
wahan usi release ki `armeabi-v7a` wali `.so` files daal do.
