Android-MuPDF
=============

MuPDF usage for magazine reading.

MuPDF developer team: http://mupdf.com/

Original source repository: http://git.ghostscript.com/?p=mupdf.git;a=summary

Native libs compiled from original source commit: http://git.ghostscript.com/?p=mupdf.git;a=tree;h=a0a9ce485579352ce9c3c4568c07e56b7029a8c8;hb=a0a9ce485579352ce9c3c4568c07e56b7029a8c8

With small change:
diff --git a/platform/android/viewer/jni/mupdf.c b/platform/android/viewer/jni/mupdf.c
index 5e04ff8..5d8b7d3 100644
--- a/platform/android/viewer/jni/mupdf.c
+++ b/platform/android/viewer/jni/mupdf.c
@@ -15,8 +15,8 @@
 #include "mupdf/fitz.h"
 #include "mupdf/pdf.h"
 
-#define JNI_FN(A) Java_com_artifex_mupdfdemo_ ## A
-#define PACKAGENAME "com/artifex/mupdfdemo"
+#define JNI_FN(A) Java_com_artifex_mupdflib_ ## A
+#define PACKAGENAME "com/artifex/mupdflib"
 
 #define LOG_TAG "libmupdf"
 #define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
