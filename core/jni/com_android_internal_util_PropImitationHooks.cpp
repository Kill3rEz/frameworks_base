#define LOG_TAG "PropImitationHooks-JNI"

#include <nativehelper/JNIHelp.h>
#include "jni.h"
#include "core_jni_helpers.h"

#include <cstring>

namespace android {

static void com_android_internal_util_PropImitationHooks_setFieldNative(
        JNIEnv* env, jclass, jclass targetClass, jobject fieldObj,
        jstring typeObj, jobject valueObj) {
    if (targetClass == nullptr || fieldObj == nullptr || typeObj == nullptr) {
        return;
    }

    jfieldID fieldID = env->FromReflectedField(fieldObj);
    if (fieldID == nullptr) {
        return;
    }

    const char* typeName = env->GetStringUTFChars(typeObj, nullptr);
    if (typeName == nullptr) {
        return;
    }

    if (strcmp(typeName, "java.lang.String") == 0) {
        env->SetStaticObjectField(targetClass, fieldID, valueObj);
    } else if (strcmp(typeName, "int") == 0) {
        jclass intClass = env->FindClass("java/lang/Integer");
        if (intClass != nullptr) {
            jmethodID intValue = env->GetMethodID(intClass, "intValue", "()I");
            if (intValue != nullptr) {
                jint val = env->CallIntMethod(valueObj, intValue);
                env->SetStaticIntField(targetClass, fieldID, val);
            }
        }
    } else if (strcmp(typeName, "long") == 0) {
        jclass longClass = env->FindClass("java/lang/Long");
        if (longClass != nullptr) {
            jmethodID longValue = env->GetMethodID(longClass, "longValue", "()J");
            if (longValue != nullptr) {
                jlong val = env->CallLongMethod(valueObj, longValue);
                env->SetStaticLongField(targetClass, fieldID, val);
            }
        }
    } else if (strcmp(typeName, "boolean") == 0) {
        jclass boolClass = env->FindClass("java/lang/Boolean");
        if (boolClass != nullptr) {
            jmethodID boolValue = env->GetMethodID(boolClass, "booleanValue", "()Z");
            if (boolValue != nullptr) {
                jboolean val = env->CallBooleanMethod(valueObj, boolValue);
                env->SetStaticBooleanField(targetClass, fieldID, val);
            }
        }
    }

    env->ReleaseStringUTFChars(typeObj, typeName);
}

static const JNINativeMethod gMethods[] = {
    { "setFieldNative",
      "(Ljava/lang/Class;Ljava/lang/reflect/Field;Ljava/lang/String;Ljava/lang/Object;)V",
      (void*) com_android_internal_util_PropImitationHooks_setFieldNative },
};

int register_com_android_internal_util_PropImitationHooks(JNIEnv* env) {
    return RegisterMethodsOrDie(env, "com/android/internal/util/PropImitationHooks",
                                gMethods, NELEM(gMethods));
}

} // namespace android
