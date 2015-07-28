LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := gif
LOCAL_SRC_FILES := \
	C:\Users\Keiran\Documents\Overchan-Android\jni\Android.mk \
	C:\Users\Keiran\Documents\Overchan-Android\jni\Application.mk \
	C:\Users\Keiran\Documents\Overchan-Android\jni\gif.c \
	C:\Users\Keiran\Documents\Overchan-Android\jni\giflib\dgif_lib.c \
	C:\Users\Keiran\Documents\Overchan-Android\jni\giflib\gifalloc.c \

LOCAL_C_INCLUDES += C:\Users\Keiran\Documents\Overchan-Android\jni
LOCAL_C_INCLUDES += C:\Users\Keiran\Documents\Overchan-Android\src\debug\jni

include $(BUILD_SHARED_LIBRARY)
