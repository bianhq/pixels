/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class io_pixelsdb_pixels_index_rocksdb_RocksetIndex */

#ifndef _Included_io_pixelsdb_pixels_index_rocksdb_RocksetIndex
#define _Included_io_pixelsdb_pixels_index_rocksdb_RocksetIndex
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     io_pixelsdb_pixels_index_rocksdb_RocksetIndex
 * Method:    CreateCloudFileSystem0
 * Signature: (Ljava/lang/String;Ljava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_io_pixelsdb_pixels_index_rocksdb_RocksetIndex_CreateCloudFileSystem0
  (JNIEnv *, jobject, jstring, jstring);

/*
 * Class:     io_pixelsdb_pixels_index_rocksdb_RocksetIndex
 * Method:    OpenDBCloud0
 * Signature: (JLjava/lang/String;Ljava/lang/String;JZ)J
 */
JNIEXPORT jlong JNICALL Java_io_pixelsdb_pixels_index_rocksdb_RocksetIndex_OpenDBCloud0
  (JNIEnv *, jobject, jlong, jstring, jstring, jlong, jboolean);

/*
 * Class:     io_pixelsdb_pixels_index_rocksdb_RocksetIndex
 * Method:    DBput0
 * Signature: (J[B[B)V
 */
JNIEXPORT void JNICALL Java_io_pixelsdb_pixels_index_rocksdb_RocksetIndex_DBput0
  (JNIEnv *, jobject, jlong, jbyteArray, jbyteArray);

/*
 * Class:     io_pixelsdb_pixels_index_rocksdb_RocksetIndex
 * Method:    DBget0
 * Signature: (J[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_io_pixelsdb_pixels_index_rocksdb_RocksetIndex_DBget0
  (JNIEnv *, jobject, jlong, jbyteArray);

/*
 * Class:     io_pixelsdb_pixels_index_rocksdb_RocksetIndex
 * Method:    DBdelete0
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_io_pixelsdb_pixels_index_rocksdb_RocksetIndex_DBdelete0
  (JNIEnv *, jobject, jlong, jbyteArray);

/*
 * Class:     io_pixelsdb_pixels_index_rocksdb_RocksetIndex
 * Method:    CloseDB0
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_io_pixelsdb_pixels_index_rocksdb_RocksetIndex_CloseDB0
  (JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif
